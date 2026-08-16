package kr.co.petcuration.cart.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.petcuration.cart.api.CartResponses.CartEnvelope;
import kr.co.petcuration.cart.api.CartResponses.CartItemResponse;
import kr.co.petcuration.cart.api.CartResponses.CartResponse;
import kr.co.petcuration.cart.api.CartResponses.ProductReference;
import kr.co.petcuration.cart.domain.CartStatus;
import kr.co.petcuration.cart.infrastructure.CartEntity;
import kr.co.petcuration.cart.infrastructure.CartItemEntity;
import kr.co.petcuration.cart.infrastructure.CartRepository;
import kr.co.petcuration.cart.infrastructure.CatalogCommerceReader;
import kr.co.petcuration.cart.infrastructure.CatalogCommerceReader.VariantData;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.identity.application.OwnerIdentity;
import kr.co.petcuration.identity.infrastructure.UserRepository;
import kr.co.petcuration.identity.infrastructure.VisitorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService implements CartCheckoutGateway {

    private static final int MAX_PURCHASE_QUANTITY = 10;
    private static final long SHIPPING_FEE = 3_000L;
    private static final long FREE_SHIPPING_THRESHOLD = 50_000L;

    private final CartRepository cartRepository;
    private final CatalogCommerceReader catalogReader;
    private final UserRepository userRepository;
    private final VisitorRepository visitorRepository;
    private final Clock clock;

    @Autowired
    public CartService(
            CartRepository cartRepository,
            CatalogCommerceReader catalogReader,
            UserRepository userRepository,
            VisitorRepository visitorRepository
    ) {
        this(cartRepository, catalogReader, userRepository, visitorRepository, Clock.systemUTC());
    }

    CartService(
            CartRepository cartRepository,
            CatalogCommerceReader catalogReader,
            UserRepository userRepository,
            VisitorRepository visitorRepository,
            Clock clock
    ) {
        this.cartRepository = cartRepository;
        this.catalogReader = catalogReader;
        this.userRepository = userRepository;
        this.visitorRepository = visitorRepository;
        this.clock = clock;
    }

    @Transactional
    public CartEnvelope getCart(OwnerIdentity owner) {
        lockOwner(owner);
        return envelope(getOrCreateCart(owner));
    }

    @Transactional
    public CartEnvelope addItem(OwnerIdentity owner, UUID variantId, int quantity) {
        lockOwner(owner);
        VariantData variant = requireVariant(variantId);
        ensurePurchasable(variant);
        CartEntity cart = getOrCreateCart(owner);
        Optional<CartItemEntity> existing = cart.getItems().stream()
                .filter(item -> item.getVariantId().equals(variantId))
                .findFirst();
        int requestedQuantity = quantity + existing.map(CartItemEntity::getQuantity).orElse(0);
        validateRequestedQuantity(requestedQuantity, variant.stockQuantity());
        Instant now = clock.instant();
        if (existing.isPresent()) {
            existing.get().changeQuantity(requestedQuantity, now);
            cart.touch(now);
        } else {
            cart.addItem(UUID.randomUUID(), variantId, requestedQuantity, variant.price(), now);
        }
        return envelope(cart);
    }

    @Transactional
    public CartEnvelope updateItem(OwnerIdentity owner, UUID itemId, int quantity) {
        lockOwner(owner);
        CartEntity cart = findActiveCartForUpdate(owner).orElseThrow(this::cartItemNotFound);
        CartItemEntity item = findOwnedItem(cart, itemId);
        VariantData variant = requireVariant(item.getVariantId());
        ensurePurchasable(variant);
        validateRequestedQuantity(quantity, variant.stockQuantity());
        Instant now = clock.instant();
        item.changeQuantity(quantity, now);
        cart.touch(now);
        return envelope(cart);
    }

    @Transactional
    public CartEnvelope deleteItem(OwnerIdentity owner, UUID itemId) {
        lockOwner(owner);
        CartEntity cart = findActiveCartForUpdate(owner).orElseThrow(this::cartItemNotFound);
        CartItemEntity item = findOwnedItem(cart, itemId);
        cart.removeItem(item, clock.instant());
        return envelope(cart);
    }

    @Transactional(readOnly = true)
    public int countItems(OwnerIdentity owner) {
        return findActiveCart(owner)
                .map(cart -> cart.getItems().stream().mapToInt(CartItemEntity::getQuantity).sum())
                .orElse(0);
    }

    @Override
    @Transactional
    public List<CheckoutItem> getCheckoutItems(OwnerIdentity owner, List<UUID> cartItemIds) {
        Set<UUID> requestedIds = validateCheckoutIds(cartItemIds);
        CartEntity cart = findActiveCartForUpdate(owner).orElseThrow(this::cartItemNotFound);
        Map<UUID, CartItemEntity> ownedItems = cart.getItems().stream()
                .filter(item -> requestedIds.contains(item.getId()))
                .collect(Collectors.toMap(CartItemEntity::getId, Function.identity()));
        if (ownedItems.size() != requestedIds.size()) {
            throw cartItemNotFound();
        }
        Map<UUID, VariantData> variants = catalogReader.findVariants(
                ownedItems.values().stream().map(CartItemEntity::getVariantId).toList()
        );
        return requestedIds.stream().map(itemId -> {
            CartItemEntity item = ownedItems.get(itemId);
            VariantData variant = variants.get(item.getVariantId());
            if (variant == null) {
                throw cartItemNotFound();
            }
            boolean available = variant.productPublic()
                    && variant.variantActive()
                    && variant.stockQuantity() >= item.getQuantity();
            return new CheckoutItem(
                    item.getId(), variant.productId(), variant.variantId(), variant.productName(),
                    variant.brandName(), variant.sku(), variant.optionLabel(), variant.thumbnailUrl(),
                    variant.price(), item.getQuantity(), available, variant.stockQuantity()
            );
        }).toList();
    }

    @Override
    @Transactional
    public void removeOrderedItems(OwnerIdentity owner, List<UUID> cartItemIds) {
        Set<UUID> requestedIds = validateCheckoutIds(cartItemIds);
        CartEntity cart = findActiveCartForUpdate(owner).orElseThrow(this::cartItemNotFound);
        List<CartItemEntity> selected = cart.getItems().stream()
                .filter(item -> requestedIds.contains(item.getId()))
                .toList();
        if (selected.size() != requestedIds.size()) {
            throw cartItemNotFound();
        }
        Instant now = clock.instant();
        selected.forEach(item -> cart.removeItem(item, now));
    }

    CartEntity getOrCreateCartForMerge(OwnerIdentity owner) {
        return findActiveCartForUpdate(owner).orElseGet(() -> cartRepository.save(
                new CartEntity(UUID.randomUUID(), owner, clock.instant())
        ));
    }

    Optional<CartEntity> findActiveCartForMerge(OwnerIdentity owner) {
        return findActiveCartForUpdate(owner);
    }

    CartEnvelope envelopeForMerge(CartEntity cart) {
        return envelope(cart);
    }

    private CartEntity getOrCreateCart(OwnerIdentity owner) {
        return findActiveCartForUpdate(owner).orElseGet(() -> cartRepository.save(
                new CartEntity(UUID.randomUUID(), owner, clock.instant())
        ));
    }

    private Optional<CartEntity> findActiveCartForUpdate(OwnerIdentity owner) {
        return owner.isMember()
                ? cartRepository.findUserCartForUpdate(owner.userId(), CartStatus.ACTIVE)
                : cartRepository.findVisitorCartForUpdate(owner.visitorId(), CartStatus.ACTIVE);
    }

    private Optional<CartEntity> findActiveCart(OwnerIdentity owner) {
        return owner.isMember()
                ? cartRepository.findUserCart(owner.userId(), CartStatus.ACTIVE)
                : cartRepository.findVisitorCart(owner.visitorId(), CartStatus.ACTIVE);
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
            visitorRepository.findByIdForUpdate(owner.visitorId()).orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND",
                    "방문자 정보를 찾을 수 없습니다.",
                    "방문자 세션을 다시 시작해 주세요."
            ));
        }
    }

    private CartEnvelope envelope(CartEntity cart) {
        Map<UUID, VariantData> variants = catalogReader.findVariants(
                cart.getItems().stream().map(CartItemEntity::getVariantId).toList()
        );
        List<CartItemResponse> responses = new ArrayList<>();
        long itemsAmount = 0;
        int itemCount = 0;
        for (CartItemEntity item : cart.getItems()) {
            VariantData variant = variants.get(item.getVariantId());
            if (variant == null) {
                continue;
            }
            String availability = availability(item, variant);
            long lineAmount = Math.multiplyExact(variant.price(), item.getQuantity());
            itemsAmount = Math.addExact(itemsAmount, lineAmount);
            itemCount += item.getQuantity();
            responses.add(new CartItemResponse(
                    item.getId(),
                    new ProductReference(
                            variant.slug(), variant.brandName(), variant.productName(), variant.thumbnailUrl()
                    ),
                    variant.variantId(), variant.sku(), variant.optionLabel(), item.getQuantity(),
                    item.getUnitPriceAtAdd(), variant.price(), lineAmount, availability,
                    item.getUnitPriceAtAdd() != variant.price(),
                    variant.productPublic() && variant.variantActive()
                            ? Math.min(MAX_PURCHASE_QUANTITY, variant.stockQuantity()) : 0
            ));
        }
        long shipping = responses.isEmpty() || itemsAmount >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
        return new CartEnvelope(new CartResponse(
                cart.getId(), cart.getStatus().name(), responses, itemsAmount, shipping,
                Math.addExact(itemsAmount, shipping), itemCount, cart.getUpdatedAt()
        ));
    }

    private String availability(CartItemEntity item, VariantData variant) {
        if (!variant.productPublic() || !variant.variantActive()) {
            return "UNAVAILABLE";
        }
        if (variant.stockQuantity() < item.getQuantity()) {
            return "OUT_OF_STOCK";
        }
        if (item.getUnitPriceAtAdd() != variant.price()) {
            return "PRICE_CHANGED";
        }
        return "AVAILABLE";
    }

    private VariantData requireVariant(UUID variantId) {
        return catalogReader.findVariant(variantId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "상품 옵션을 찾을 수 없습니다.",
                "요청한 상품 옵션이 존재하지 않습니다."
        ));
    }

    private void ensurePurchasable(VariantData variant) {
        if (!variant.productPublic() || !variant.variantActive()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_UNAVAILABLE",
                    "현재 구매할 수 없는 상품입니다.",
                    "상품 또는 옵션의 판매 상태를 확인해 주세요."
            );
        }
        if (variant.stockQuantity() <= 0) {
            throw stockConflict();
        }
    }

    private void validateRequestedQuantity(int quantity, int stockQuantity) {
        if (quantity > MAX_PURCHASE_QUANTITY) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUANTITY_LIMIT_EXCEEDED",
                    "구매 가능 수량을 초과했습니다.",
                    "상품 옵션당 최대 10개까지 담을 수 있습니다."
            );
        }
        if (quantity > stockQuantity) {
            throw stockConflict();
        }
    }

    private Set<UUID> validateCheckoutIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "요청 형식이 올바르지 않습니다.",
                    "하나 이상의 장바구니 항목을 선택해 주세요."
            );
        }
        Set<UUID> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "요청 형식이 올바르지 않습니다.",
                    "중복된 장바구니 항목이 있습니다."
            );
        }
        return unique;
    }

    private CartItemEntity findOwnedItem(CartEntity cart, UUID itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(this::cartItemNotFound);
    }

    private ApiException cartItemNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "장바구니 항목을 찾을 수 없습니다.",
                "요청한 항목이 현재 장바구니에 없습니다."
        );
    }

    private ApiException stockConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "STOCK_CONFLICT",
                "재고가 부족합니다.",
                "현재 구매 가능한 수량을 확인해 주세요."
        );
    }
}
