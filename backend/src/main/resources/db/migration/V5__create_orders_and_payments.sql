CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    order_type VARCHAR(20) NOT NULL,
    order_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status VARCHAR(30) NOT NULL DEFAULT 'READY',
    buyer_name VARCHAR(100) NOT NULL,
    buyer_email VARCHAR(320) NOT NULL,
    buyer_phone VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    address1 VARCHAR(255) NOT NULL,
    address2 VARCHAR(255),
    delivery_message VARCHAR(500),
    items_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    shipping_amount BIGINT NOT NULL DEFAULT 0,
    total_amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    guest_lookup_token_hash VARCHAR(128) UNIQUE,
    idempotency_key UUID NOT NULL UNIQUE,
    request_hash VARCHAR(128) NOT NULL,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    ordered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_orders_type CHECK (order_type IN ('MEMBER', 'GUEST')),
    CONSTRAINT ck_orders_owner CHECK (
        (order_type = 'MEMBER' AND user_id IS NOT NULL AND guest_lookup_token_hash IS NULL)
        OR (order_type = 'GUEST' AND user_id IS NULL AND guest_lookup_token_hash IS NOT NULL)
    ),
    CONSTRAINT ck_orders_status CHECK (order_status IN ('PENDING_PAYMENT', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCEL_REQUESTED', 'CANCELLED')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('READY', 'PROCESSING', 'APPROVED', 'FAILED', 'UNKNOWN', 'CANCELLED', 'PARTIAL_CANCELLED')),
    CONSTRAINT ck_orders_amounts CHECK (items_amount >= 0 AND discount_amount >= 0 AND shipping_amount >= 0 AND total_amount >= 0)
);

CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX ix_orders_status_created ON orders (order_status, created_at DESC);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    cart_item_id UUID,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    variant_id UUID NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    product_name VARCHAR(200) NOT NULL,
    brand_name VARCHAR(150) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    option_label VARCHAR(150) NOT NULL,
    image_url VARCHAR(500),
    unit_price BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    line_amount BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_items_amount CHECK (unit_price >= 0 AND quantity > 0 AND line_amount >= 0)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    variant_id UUID NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_reservation_order_variant UNIQUE (order_id, variant_id),
    CONSTRAINT ck_inventory_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservations_status CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX ix_inventory_reservations_expiry ON inventory_reservations (status, expires_at);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    provider VARCHAR(30) NOT NULL,
    method VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    amount BIGINT NOT NULL,
    provider_payment_key VARCHAR(200) UNIQUE,
    idempotency_key UUID UNIQUE,
    request_hash VARCHAR(128),
    test_payment BOOLEAN NOT NULL DEFAULT TRUE,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    approved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payments_status CHECK (status IN ('READY', 'PROCESSING', 'APPROVED', 'FAILED', 'UNKNOWN', 'CANCELLED', 'PARTIAL_CANCELLED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
);

CREATE INDEX ix_payments_order_created ON payments (order_id, created_at DESC);

CREATE TABLE payment_idempotency_records (
    idempotency_key UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    request_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_payment_idempotency_payment ON payment_idempotency_records (payment_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_by_user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, created_at);

CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    payment_id UUID REFERENCES payments (id) ON DELETE RESTRICT,
    provider VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(200),
    event_type VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(200) NOT NULL,
    before_summary JSONB,
    after_summary JSONB,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_admin_audit_created ON admin_audit_logs (created_at DESC);
