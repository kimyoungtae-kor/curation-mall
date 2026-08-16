package kr.co.petcuration.order.application;

import java.util.List;
import java.util.UUID;

public interface OrderCartGateway {

    List<CartLine> load(CommerceActor actor, List<UUID> cartItemIds);

    List<CartLine> loadForOrderCreation(CommerceActor actor, List<UUID> cartItemIds);

    int remove(CommerceActor actor, List<UUID> cartItemIds);

    record CartLine(
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
            int stockQuantity,
            boolean available
    ) {
    }
}
