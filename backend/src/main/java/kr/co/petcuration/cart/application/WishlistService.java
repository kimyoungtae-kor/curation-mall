package kr.co.petcuration.cart.application;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.petcuration.cart.api.CartResponses.PageMetadata;
import kr.co.petcuration.cart.api.CartResponses.WishlistListResponse;
import kr.co.petcuration.cart.api.CartResponses.WishlistMutationEnvelope;
import kr.co.petcuration.cart.api.CartResponses.WishlistMutationResponse;
import kr.co.petcuration.cart.api.CartResponses.WishlistProductResponse;
import kr.co.petcuration.cart.infrastructure.CatalogCommerceReader;
import kr.co.petcuration.cart.infrastructure.CatalogCommerceReader.ProductData;
import kr.co.petcuration.cart.infrastructure.WishlistItemEntity;
import kr.co.petcuration.cart.infrastructure.WishlistItemRepository;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.identity.application.OwnerIdentity;
import kr.co.petcuration.identity.infrastructure.UserRepository;
import kr.co.petcuration.identity.infrastructure.VisitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final CatalogCommerceReader catalogReader;
    private final UserRepository userRepository;
    private final VisitorRepository visitorRepository;
    private final Clock clock;

    public WishlistService(
            WishlistItemRepository wishlistRepository,
            CatalogCommerceReader catalogReader,
            UserRepository userRepository,
            VisitorRepository visitorRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.catalogReader = catalogReader;
        this.userRepository = userRepository;
        this.visitorRepository = visitorRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public WishlistListResponse getWishlist(OwnerIdentity owner, int pageNumber, int size) {
        lockOwner(owner);
        Page<WishlistItemEntity> page = owner.isMember()
                ? wishlistRepository.findByUserIdOrderByCreatedAtDesc(
                        owner.userId(), PageRequest.of(pageNumber, size))
                : wishlistRepository.findByVisitorIdOrderByCreatedAtDesc(
                        owner.visitorId(), PageRequest.of(pageNumber, size));
        Map<UUID, ProductData> products = catalogReader.findProducts(
                        page.getContent().stream().map(WishlistItemEntity::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(ProductData::productId, Function.identity()));
        var responses = page.getContent().stream()
                .map(WishlistItemEntity::getProductId)
                .map(products::get)
                .filter(product -> product != null && product.productPublic())
                .map(product -> new WishlistProductResponse(
                        product.productId(), product.slug(), product.brandName(), product.name(),
                        product.thumbnailUrl(), product.minimumPrice(), true
                ))
                .toList();
        return new WishlistListResponse(
                responses,
                new PageMetadata(
                        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                        page.isFirst(), page.isLast()
                )
        );
    }

    @Transactional
    public WishlistMutationEnvelope add(OwnerIdentity owner, UUID productId) {
        lockOwner(owner);
        ProductData product = catalogReader.findProduct(productId)
                .filter(ProductData::productPublic)
                .orElseThrow(this::productNotFound);
        Optional<WishlistItemEntity> existing = find(owner, product.productId());
        if (existing.isEmpty()) {
            wishlistRepository.save(new WishlistItemEntity(
                    UUID.randomUUID(), owner, product.productId(), clock.instant()
            ));
        }
        return new WishlistMutationEnvelope(new WishlistMutationResponse(
                product.productId(), true, count(owner)
        ));
    }

    @Transactional
    public void delete(OwnerIdentity owner, UUID productId) {
        lockOwner(owner);
        find(owner, productId).ifPresent(wishlistRepository::delete);
    }

    @Transactional(readOnly = true)
    public long count(OwnerIdentity owner) {
        return owner.isMember()
                ? wishlistRepository.countByUserId(owner.userId())
                : wishlistRepository.countByVisitorId(owner.visitorId());
    }

    private Optional<WishlistItemEntity> find(OwnerIdentity owner, UUID productId) {
        return owner.isMember()
                ? wishlistRepository.findByUserIdAndProductId(owner.userId(), productId)
                : wishlistRepository.findByVisitorIdAndProductId(owner.visitorId(), productId);
    }

    private void lockOwner(OwnerIdentity owner) {
        if (owner.isMember()) {
            userRepository.findByIdForUpdate(owner.userId()).orElseThrow(() -> new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다.",
                    "회원 세션을 다시 확인해 주세요."
            ));
        } else {
            visitorRepository.findByIdForUpdate(owner.visitorId()).orElseThrow(this::productNotFound);
        }
    }

    private ApiException productNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "상품을 찾을 수 없습니다.",
                "요청한 상품이 없거나 공개되지 않았습니다."
        );
    }
}
