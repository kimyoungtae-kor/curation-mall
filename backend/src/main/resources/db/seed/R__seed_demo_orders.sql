-- Fictional order history for local/test demonstrations only.
INSERT INTO orders (
    id, order_number, user_id, order_type, order_status, payment_status,
    buyer_name, buyer_email, buyer_phone,
    recipient_name, recipient_phone, postal_code, address1, address2, delivery_message,
    items_amount, discount_amount, shipping_amount, total_amount, currency,
    guest_lookup_token_hash, idempotency_key, request_hash,
    reservation_expires_at, ordered_at, paid_at, cancelled_at, created_at, updated_at
)
VALUES
    (
        '91000000-0000-0000-0000-000000000001',
        'P20260812-DEMO0001',
        '11000000-0000-0000-0000-000000000001',
        'MEMBER', 'PAID', 'APPROVED',
        '김데모', 'demo@example.com', '01012345678',
        '김데모', '01012345678', '06234', '서울특별시 강남구 데모로 10', '101호', '문 앞에 놓아 주세요',
        62000, 0, 0, 62000, 'KRW',
        NULL,
        '91000000-0000-0000-0000-000000000101',
        'demo-member-order-request-hash',
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 40 minutes',
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 58 minutes',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 58 minutes'
    ),
    (
        '91000000-0000-0000-0000-000000000002',
        'P20260812-DEMO0002',
        NULL,
        'GUEST', 'CANCELLED', 'FAILED',
        '박비회원', 'guest-demo@example.com', '01098765432',
        '박비회원', '01098765432', '04524', '서울특별시 중구 시연로 20', NULL, NULL,
        39000, 0, 3000, 42000, 'KRW',
        'e38151e6d178d17d7479e95a2d36f123dfa8d0ea7d359b670f0366a62d61a23a',
        '91000000-0000-0000-0000-000000000102',
        'demo-guest-order-request-hash',
        CURRENT_TIMESTAMP - INTERVAL '23 hours 40 minutes',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '23 hours 59 minutes',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        CURRENT_TIMESTAMP - INTERVAL '23 hours 59 minutes'
    )
ON CONFLICT (id) DO UPDATE SET
    order_status = EXCLUDED.order_status,
    payment_status = EXCLUDED.payment_status,
    updated_at = EXCLUDED.updated_at;

INSERT INTO order_items (
    id, order_id, product_id, variant_id, product_name, brand_name, sku, option_label,
    image_url, unit_price, quantity, line_amount, created_at
)
VALUES
    (
        '92000000-0000-0000-0000-000000000001',
        '91000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000006',
        '60000000-0000-0000-0000-000000000106',
        '브리즈 순환 급수기', 'Morrow Pet', 'DEMO-ITEM-06', '기본 옵션',
        '/media/demo/catalog/oasis-water-bowl.webp', 62000, 1, 62000,
        CURRENT_TIMESTAMP - INTERVAL '2 days'
    ),
    (
        '92000000-0000-0000-0000-000000000002',
        '91000000-0000-0000-0000-000000000002',
        '50000000-0000-0000-0000-000000000008',
        '60000000-0000-0000-0000-000000000108',
        '트레일 컴포트 하네스', 'Pawform', 'DEMO-ITEM-08', '기본 옵션',
        '/media/demo/catalog/cloud-fit-car-seat.webp', 39000, 1, 39000,
        CURRENT_TIMESTAMP - INTERVAL '1 day'
    )
ON CONFLICT (id) DO UPDATE SET
    product_name = EXCLUDED.product_name,
    brand_name = EXCLUDED.brand_name,
    unit_price = EXCLUDED.unit_price,
    quantity = EXCLUDED.quantity,
    line_amount = EXCLUDED.line_amount;

INSERT INTO payments (
    id, order_id, provider, method, status, amount, provider_payment_key,
    idempotency_key, request_hash, test_payment, failure_code, failure_message,
    approved_at, created_at, updated_at
)
VALUES
    (
        '93000000-0000-0000-0000-000000000001',
        '91000000-0000-0000-0000-000000000001',
        'SIMULATED', 'CARD', 'APPROVED', 62000, 'demo-approved-payment-key',
        '93000000-0000-0000-0000-000000000101', 'demo-approved-payment-request', TRUE,
        NULL, NULL,
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 58 minutes',
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 58 minutes'
    ),
    (
        '93000000-0000-0000-0000-000000000002',
        '91000000-0000-0000-0000-000000000002',
        'SIMULATED', 'CARD', 'FAILED', 42000, NULL,
        '93000000-0000-0000-0000-000000000102', 'demo-failed-payment-request', TRUE,
        'SIMULATED_FAILURE', '시연용 결제 실패',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        CURRENT_TIMESTAMP - INTERVAL '23 hours 59 minutes'
    )
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    failure_code = EXCLUDED.failure_code,
    failure_message = EXCLUDED.failure_message,
    updated_at = EXCLUDED.updated_at;

INSERT INTO inventory_reservations (
    id, order_id, variant_id, quantity, status, expires_at, created_at, updated_at
)
VALUES
    (
        '94000000-0000-0000-0000-000000000001',
        '91000000-0000-0000-0000-000000000001',
        '60000000-0000-0000-0000-000000000106', 1, 'COMMITTED',
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 40 minutes',
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 58 minutes'
    ),
    (
        '94000000-0000-0000-0000-000000000002',
        '91000000-0000-0000-0000-000000000002',
        '60000000-0000-0000-0000-000000000108', 1, 'RELEASED',
        CURRENT_TIMESTAMP - INTERVAL '23 hours 40 minutes',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        CURRENT_TIMESTAMP - INTERVAL '23 hours 59 minutes'
    )
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at;

INSERT INTO order_status_history (
    id, order_id, from_status, to_status, reason, created_at
)
VALUES
    (
        '95000000-0000-0000-0000-000000000001',
        '91000000-0000-0000-0000-000000000001',
        NULL, 'PENDING_PAYMENT', '시연 주문 생성', CURRENT_TIMESTAMP - INTERVAL '2 days'
    ),
    (
        '95000000-0000-0000-0000-000000000002',
        '91000000-0000-0000-0000-000000000001',
        'PENDING_PAYMENT', 'PAID', '시연 테스트 결제 승인', CURRENT_TIMESTAMP - INTERVAL '1 day 23 hours 58 minutes'
    ),
    (
        '95000000-0000-0000-0000-000000000003',
        '91000000-0000-0000-0000-000000000002',
        NULL, 'PENDING_PAYMENT', '시연 주문 생성', CURRENT_TIMESTAMP - INTERVAL '1 day'
    ),
    (
        '95000000-0000-0000-0000-000000000004',
        '91000000-0000-0000-0000-000000000002',
        'PENDING_PAYMENT', 'CANCELLED', '시연 테스트 결제 실패', CURRENT_TIMESTAMP - INTERVAL '23 hours 59 minutes'
    )
ON CONFLICT (id) DO UPDATE SET
    reason = EXCLUDED.reason;
