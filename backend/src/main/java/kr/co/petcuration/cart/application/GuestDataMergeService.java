package kr.co.petcuration.cart.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.cart.infrastructure.CartEntity;
import kr.co.petcuration.cart.infrastructure.CartItemEntity;
import kr.co.petcuration.cart.infrastructure.CatalogCommerceReader;
import kr.co.petcuration.cart.infrastructure.CatalogCommerceReader.VariantData;
import kr.co.petcuration.cart.infrastructure.WishlistItemRepository;
import kr.co.petcuration.identity.api.IdentityResponses.MergeAdjustmentResponse;
import kr.co.petcuration.identity.api.IdentityResponses.MergeResultResponse;
import kr.co.petcuration.identity.application.OwnerIdentity;
import kr.co.petcuration.identity.infrastructure.UserRepository;
import kr.co.petcuration.identity.infrastructure.VisitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestDataMergeService {

    private static final int PURCHASE_LIMIT = 10;

    private final CartService cartService;
    private final WishlistItemRepository wishlistRepository;
    private final CatalogCommerceReader catalogReader;
    private final UserRepository userRepository;
    private final VisitorRepository visitorRepository;
    private final Clock clock;

    public GuestDataMergeService(
            CartService cartService,
            WishlistItemRepository wishlistRepository,
            CatalogCommerceReader catalogReader,
            UserRepository userRepository,
            VisitorRepository visitorRepository
    ) {
        this.cartService = cartService;
        this.wishlistRepository = wishlistRepository;
        this.catalogReader = catalogReader;
        this.userRepository = userRepository;
        this.visitorRepository = visitorRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public MergeResultResponse merge(UUID userId, UUID visitorId) {
        OwnerIdentity memberOwner = OwnerIdentity.member(userId);
        userRepository.findByIdForUpdate(userId).orElseThrow();
        if (visitorId == null || visitorRepository.findByIdForUpdate(visitorId).isEmpty()) {
            return currentResult(memberOwner, false, List.of());
        }

        OwnerIdentity visitorOwner = OwnerIdentity.visitor(visitorId);
        Optional<CartEntity> visitorCart = cartService.findActiveCartForMerge(visitorOwner);
        boolean hasGuestData = visitorCart.isPresent();
        List<MergeAdjustmentResponse> adjustments = new ArrayList<>();

        if (visitorCart.isPresent()) {
            mergeCart(memberOwner, visitorCart.get(), adjustments);
        }
        wishlistRepository.deleteByVisitorId(visitorId);
        return currentResult(memberOwner, hasGuestData, adjustments);
    }

    @Transactional
    public MergeResultResponse discardForSignup(UUID userId, UUID visitorId) {
        OwnerIdentity memberOwner = OwnerIdentity.member(userId);
        userRepository.findByIdForUpdate(userId).orElseThrow();
        if (visitorId == null || visitorRepository.findByIdForUpdate(visitorId).isEmpty()) {
            return currentResult(memberOwner, false, List.of());
        }

        cartService.findActiveCartForMerge(OwnerIdentity.visitor(visitorId))
                .ifPresent(cart -> cart.markExpired(clock.instant()));
        wishlistRepository.deleteByVisitorId(visitorId);
        return currentResult(memberOwner, false, List.of());
    }

    private void mergeCart(
            OwnerIdentity memberOwner,
            CartEntity visitorCart,
            List<MergeAdjustmentResponse> adjustments
    ) {
        CartEntity memberCart = cartService.getOrCreateCartForMerge(memberOwner);
        Instant now = clock.instant();
        for (CartItemEntity visitorItem : visitorCart.getItems()) {
            VariantData variant = catalogReader.findVariant(visitorItem.getVariantId()).orElse(null);
            CartItemEntity memberItem = memberCart.getItems().stream()
                    .filter(item -> item.getVariantId().equals(visitorItem.getVariantId()))
                    .findFirst()
                    .orElse(null);
            int memberQuantity = memberItem == null ? 0 : memberItem.getQuantity();
            int visitorQuantity = visitorItem.getQuantity();

            if (variant == null || !variant.productPublic() || !variant.variantActive()) {
                adjustments.add(new MergeAdjustmentResponse(
                        visitorItem.getVariantId(), memberQuantity, visitorQuantity,
                        memberQuantity, "VARIANT_UNAVAILABLE"
                ));
                continue;
            }

            int combined = memberQuantity + visitorQuantity;
            int allowed = Math.min(PURCHASE_LIMIT, variant.stockQuantity());
            int mergedQuantity = Math.max(memberQuantity, Math.min(combined, allowed));
            if (mergedQuantity > memberQuantity) {
                if (memberItem == null) {
                    memberCart.addItem(
                            UUID.randomUUID(), visitorItem.getVariantId(), mergedQuantity,
                            visitorItem.getUnitPriceAtAdd(), now
                    );
                } else {
                    memberItem.changeQuantity(mergedQuantity, now);
                    memberCart.touch(now);
                }
            }
            if (mergedQuantity != combined) {
                String reason = variant.stockQuantity() < PURCHASE_LIMIT ? "STOCK_LIMIT" : "PURCHASE_LIMIT";
                adjustments.add(new MergeAdjustmentResponse(
                        visitorItem.getVariantId(), memberQuantity, visitorQuantity, mergedQuantity, reason
                ));
            }
        }
        visitorCart.markMerged(now);
    }

    private MergeResultResponse currentResult(
            OwnerIdentity memberOwner,
            boolean merged,
            List<MergeAdjustmentResponse> adjustments
    ) {
        int cartCount = cartService.countItems(memberOwner);
        long wishlistCount = wishlistRepository.countByUserId(memberOwner.userId());
        return new MergeResultResponse(merged, cartCount, wishlistCount, adjustments);
    }
}
