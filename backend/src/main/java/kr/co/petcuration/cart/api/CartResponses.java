package kr.co.petcuration.cart.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CartResponses {

    private CartResponses() {
    }

    public record CartEnvelope(CartResponse data) {
    }

    public record CartResponse(
            UUID id,
            String status,
            List<CartItemResponse> items,
            long itemsAmount,
            long shippingAmountEstimate,
            long totalAmountEstimate,
            int itemCount,
            Instant updatedAt
    ) {
        public CartResponse {
            items = List.copyOf(items);
        }
    }

    public record CartItemResponse(
            UUID id,
            ProductReference product,
            UUID variantId,
            String sku,
            String optionLabel,
            int quantity,
            long unitPriceAtAdd,
            long currentUnitPrice,
            long lineAmount,
            String availability,
            boolean priceChanged,
            int maxPurchaseQuantity
    ) {
    }

    public record ProductReference(
            String slug,
            String brandName,
            String name,
            String thumbnailUrl
    ) {
    }

    public record WishlistMutationEnvelope(WishlistMutationResponse data) {
    }

    public record WishlistMutationResponse(UUID productId, boolean wishlisted, long wishlistCount) {
    }

    public record WishlistListResponse(List<WishlistProductResponse> data, PageMetadata page) {
        public WishlistListResponse {
            data = List.copyOf(data);
        }
    }

    public record WishlistProductResponse(
            UUID productId,
            String slug,
            String brandName,
            String name,
            String thumbnailUrl,
            Long minimumPrice,
            boolean wishlisted
    ) {
    }

    public record PageMetadata(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }
}
