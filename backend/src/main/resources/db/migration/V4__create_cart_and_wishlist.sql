CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    visitor_id UUID REFERENCES visitors (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_carts_owner CHECK (
        (user_id IS NOT NULL AND visitor_id IS NULL)
        OR (user_id IS NULL AND visitor_id IS NOT NULL)
    ),
    CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'MERGED', 'ORDERED', 'EXPIRED'))
);

CREATE UNIQUE INDEX uq_active_cart_user
    ON carts (user_id)
    WHERE status = 'ACTIVE' AND user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_active_cart_visitor
    ON carts (visitor_id)
    WHERE status = 'ACTIVE' AND visitor_id IS NOT NULL;

CREATE INDEX ix_carts_user ON carts (user_id, status);
CREATE INDEX ix_carts_visitor ON carts (visitor_id, status);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    unit_price_at_add BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cart_items_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT ck_cart_items_price CHECK (unit_price_at_add >= 0)
);

CREATE INDEX ix_cart_items_cart ON cart_items (cart_id, created_at, id);

CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    visitor_id UUID REFERENCES visitors (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wishlist_items_owner CHECK (
        (user_id IS NOT NULL AND visitor_id IS NULL)
        OR (user_id IS NULL AND visitor_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_wishlist_user_product
    ON wishlist_items (user_id, product_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_wishlist_visitor_product
    ON wishlist_items (visitor_id, product_id)
    WHERE visitor_id IS NOT NULL;

CREATE INDEX ix_wishlist_user_created ON wishlist_items (user_id, created_at DESC);
CREATE INDEX ix_wishlist_visitor_created ON wishlist_items (visitor_id, created_at DESC);
