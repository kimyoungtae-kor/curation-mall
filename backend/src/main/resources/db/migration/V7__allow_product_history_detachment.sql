-- Historical order rows keep their immutable snapshots even after an administrator
-- explicitly confirms deletion of the current catalog product.
ALTER TABLE order_items
    DROP CONSTRAINT order_items_product_id_fkey,
    DROP CONSTRAINT order_items_variant_id_fkey,
    ALTER COLUMN product_id DROP NOT NULL,
    ALTER COLUMN variant_id DROP NOT NULL,
    ADD CONSTRAINT order_items_product_id_fkey
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    ADD CONSTRAINT order_items_variant_id_fkey
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL;

-- Only non-active reservations may be detached by the application. ACTIVE rows are
-- blocked before product deletion because failure/expiry still needs the variant.
ALTER TABLE inventory_reservations
    DROP CONSTRAINT inventory_reservations_variant_id_fkey,
    ALTER COLUMN variant_id DROP NOT NULL,
    ADD CONSTRAINT inventory_reservations_variant_id_fkey
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL;
