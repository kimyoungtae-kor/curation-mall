package kr.co.petcuration.cart.application;

import java.util.List;
import java.util.UUID;
import kr.co.petcuration.identity.application.OwnerIdentity;

public interface CartCheckoutGateway {

    List<CheckoutItem> getCheckoutItems(OwnerIdentity owner, List<UUID> cartItemIds);

    void removeOrderedItems(OwnerIdentity owner, List<UUID> cartItemIds);

    record CheckoutItem(
            UUID cartItemId,
            UUID productId,
            UUID variantId,
            String productName,
            String brandName,
            String sku,
            String optionLabel,
            String imageUrl,
            long unitPrice,
            int quantity,
            boolean available,
            int stockQuantity
    ) {
    }
}
