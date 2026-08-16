CREATE UNIQUE INDEX uq_order_items_cart_item
    ON order_items (cart_item_id)
    WHERE cart_item_id IS NOT NULL;
