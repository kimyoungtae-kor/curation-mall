package kr.co.petcuration.order.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.order.api.OrderApiModels;
import kr.co.petcuration.order.api.OrderApiModels.CreateOrderRequest;
import kr.co.petcuration.order.api.OrderApiModels.CreateOrderResult;
import kr.co.petcuration.order.api.OrderApiModels.OrderDetail;
import kr.co.petcuration.order.api.OrderApiModels.OrderItem;
import kr.co.petcuration.order.api.OrderApiModels.OrderListResponse;
import kr.co.petcuration.order.api.OrderApiModels.OrderSummary;
import kr.co.petcuration.order.api.OrderApiModels.PageMetadata;
import kr.co.petcuration.order.api.OrderApiModels.PaymentAttempt;
import kr.co.petcuration.order.api.OrderApiModels.Quote;
import kr.co.petcuration.order.api.OrderApiModels.QuoteLine;
import kr.co.petcuration.order.api.OrderApiModels.StatusHistory;
import kr.co.petcuration.order.application.OrderCartGateway.CartLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final long FREE_SHIPPING_THRESHOLD = 50_000L;
    private static final long STANDARD_SHIPPING = 3_000L;
    private static final int RESERVATION_MINUTES = 20;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final OrderCartGateway cartGateway;
    private final Clock clock;
    private final byte[] guestLookupTokenSecret;

    @Autowired
    public OrderService(
            JdbcTemplate jdbcTemplate,
            OrderCartGateway cartGateway,
            @Value("${app.order.guest-lookup-token-secret}") String guestLookupTokenSecret
    ) {
        this(jdbcTemplate, cartGateway, Clock.systemUTC(), guestLookupTokenSecret);
    }

    OrderService(
            JdbcTemplate jdbcTemplate,
            OrderCartGateway cartGateway,
            Clock clock,
            String guestLookupTokenSecret
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cartGateway = cartGateway;
        this.clock = clock;
        this.guestLookupTokenSecret = validateGuestLookupTokenSecret(guestLookupTokenSecret);
    }

    @Transactional(readOnly = true)
    public Quote quote(String requestedType, List<UUID> cartItemIds, CommerceActor actor) {
        String orderType = validateOrderType(requestedType, actor);
        List<CartLine> lines = loadPurchasableLines(actor, cartItemIds, false);
        long itemsAmount = lines.stream().mapToLong(line -> Math.multiplyExact(line.unitPrice(), line.quantity())).sum();
        long shippingAmount = shippingAmount(itemsAmount);
        List<QuoteLine> responseLines = lines.stream().map(line -> new QuoteLine(
                line.cartItemId(),
                line.variantId(),
                line.productName(),
                line.optionLabel(),
                line.quantity(),
                line.unitPrice(),
                Math.multiplyExact(line.unitPrice(), line.quantity()),
                "AVAILABLE"
        )).toList();
        return new Quote(orderType, responseLines, itemsAmount, 0L, shippingAmount,
                itemsAmount + shippingAmount, "KRW", List.of(), Instant.now(clock));
    }

    @Transactional
    public CreateOrderResult create(
            CreateOrderRequest request,
            UUID idempotencyKey,
            CommerceActor actor
    ) {
        String orderType = validateOrderType(request.orderType(), actor);
        String requestHash = hashRequest(request, actor);
        lockIdempotency(idempotencyKey);

        List<ExistingOrder> existing = jdbcTemplate.query("""
                SELECT o.order_number, o.order_type, o.request_hash, p.id AS payment_id
                  FROM orders o
                  JOIN payments p ON p.order_id = o.id
                 WHERE o.idempotency_key = ?
                 ORDER BY p.created_at
                 LIMIT 1
                """, (rs, rowNum) -> new ExistingOrder(
                rs.getString("order_number"),
                rs.getString("order_type"),
                rs.getString("request_hash"),
                rs.getObject("payment_id", UUID.class)
        ), idempotencyKey);
        if (!existing.isEmpty()) {
            ExistingOrder prior = existing.getFirst();
            if (!prior.requestHash().equals(requestHash)) {
                throw conflict("IDEMPOTENCY_CONFLICT", "같은 멱등성 키가 다른 주문 요청에 사용되었습니다.");
            }
            return replayResult(prior.orderNumber(), prior.orderType(), prior.paymentId(), idempotencyKey,
                    prior.requestHash());
        }

        List<CartLine> lines = loadPurchasableLines(actor, request.cartItemIds(), true);
        long itemsAmount = lines.stream().mapToLong(line -> Math.multiplyExact(line.unitPrice(), line.quantity())).sum();
        long shippingAmount = shippingAmount(itemsAmount);
        long totalAmount = itemsAmount + shippingAmount;
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(RESERVATION_MINUTES * 60L);
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String orderNumber = newOrderNumber();
        String guestToken = "GUEST".equals(orderType)
                ? guestLookupToken(orderNumber, idempotencyKey, requestHash)
                : null;
        String guestTokenHash = guestToken == null ? null : CommerceActorResolver.sha256(guestToken);
        UUID memberId = "MEMBER".equals(orderType) ? actor.userId() : null;

        lines.stream().sorted(java.util.Comparator.comparing(CartLine::variantId)).forEach(line -> {
            int updated = jdbcTemplate.update("""
                    UPDATE product_variants pv
                       SET stock_quantity = pv.stock_quantity - ?, updated_at = CURRENT_TIMESTAMP, version = pv.version + 1
                      FROM products p
                     WHERE pv.id = ?
                       AND p.id = pv.product_id
                       AND pv.status = 'ACTIVE'
                       AND p.status = 'PUBLISHED'
                       AND p.published_at IS NOT NULL
                       AND p.published_at <= CURRENT_TIMESTAMP
                       AND pv.stock_quantity >= ?
                    """, line.quantity(), line.variantId(), line.quantity());
            if (updated != 1) {
                throw conflict("STOCK_CONFLICT", "결제 가능한 재고가 부족합니다. 장바구니를 다시 확인해 주세요.");
            }
        });

        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, order_number, user_id, order_type, order_status, payment_status,
                    buyer_name, buyer_email, buyer_phone,
                    recipient_name, recipient_phone, postal_code, address1, address2, delivery_message,
                    items_amount, discount_amount, shipping_amount, total_amount, currency,
                    guest_lookup_token_hash, idempotency_key, request_hash,
                    reservation_expires_at, ordered_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', 'READY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 'KRW', ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId, orderNumber, memberId, orderType,
                request.buyer().name(), request.buyer().email().toLowerCase(Locale.ROOT), request.buyer().phone(),
                request.shipping().recipientName(), request.shipping().recipientPhone(), request.shipping().postalCode(),
                request.shipping().address1(), blankToNull(request.shipping().address2()),
                blankToNull(request.shipping().deliveryMessage()), itemsAmount, shippingAmount, totalAmount,
                guestTokenHash, idempotencyKey, requestHash,
                Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        for (CartLine line : lines) {
            long lineAmount = Math.multiplyExact(line.unitPrice(), line.quantity());
            jdbcTemplate.update("""
                    INSERT INTO order_items (
                        id, order_id, cart_item_id, product_id, variant_id, product_name, brand_name,
                        sku, option_label, image_url, unit_price, quantity, line_amount
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), orderId, line.cartItemId(), line.productId(), line.variantId(),
                    line.productName(), line.brandName(), line.sku(), line.optionLabel(), line.imageUrl(),
                    line.unitPrice(), line.quantity(), lineAmount);
            jdbcTemplate.update("""
                    INSERT INTO inventory_reservations (id, order_id, variant_id, quantity, status, expires_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                    """, UUID.randomUUID(), orderId, line.variantId(), line.quantity(), Timestamp.from(expiresAt));
        }
        jdbcTemplate.update("""
                INSERT INTO payments (id, order_id, provider, status, amount, test_payment)
                VALUES (?, ?, 'SIMULATED', 'READY', ?, TRUE)
                """, paymentId, orderId, totalAmount);
        jdbcTemplate.update("""
                INSERT INTO order_status_history (id, order_id, from_status, to_status, reason)
                VALUES (?, ?, NULL, 'PENDING_PAYMENT', '주문 생성')
                """, UUID.randomUUID(), orderId);
        int removedCartItems = cartGateway.remove(actor, request.cartItemIds());
        if (removedCartItems != lines.size()) {
            throw conflict("CART_CHANGED", "주문 처리 중 장바구니가 변경되었습니다. 장바구니를 다시 확인해 주세요.");
        }

        OrderSummary summary = new OrderSummary(orderNumber, orderType, "PENDING_PAYMENT", "READY",
                itemsAmount, 0L, shippingAmount, totalAmount, "KRW", expiresAt, now,
                lines.stream().mapToInt(CartLine::quantity).sum(), lines.getFirst().productName());
        PaymentAttempt payment = new PaymentAttempt(paymentId, "SIMULATED", null, totalAmount,
                "READY", null, true);
        return new CreateOrderResult(false, summary, payment, guestToken);
    }

    @Transactional(readOnly = true)
    public OrderListResponse listMember(CommerceActor actor, int page, int size) {
        requireMember(actor);
        long total = jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Long.class,
                actor.userId());
        List<OrderSummary> orders = jdbcTemplate.query("""
                SELECT o.*,
                       (SELECT coalesce(sum(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
                       (SELECT oi.product_name FROM order_items oi WHERE oi.order_id = o.id ORDER BY oi.created_at LIMIT 1) AS representative_item_name
                  FROM orders o
                 WHERE o.user_id = ?
                 ORDER BY o.created_at DESC
                 LIMIT ? OFFSET ?
                """, (rs, rowNum) -> summary(rs), actor.userId(), size, Math.multiplyExact(page, size));
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new OrderListResponse(orders,
                new PageMetadata(page, size, total, totalPages, page == 0, page + 1 >= totalPages));
    }

    @Transactional(readOnly = true)
    public OrderDetail memberDetail(String orderNumber, CommerceActor actor) {
        requireMember(actor);
        return detail(orderNumber, "o.user_id = ?", actor.userId());
    }

    @Transactional(readOnly = true)
    public OrderDetail guestDetail(String orderNumber, String guestLookupToken) {
        try {
            return detail(orderNumber, "o.order_type = 'GUEST' AND o.guest_lookup_token_hash = ?",
                    CommerceActorResolver.sha256(guestLookupToken));
        } catch (ApiException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GUEST_ORDER_VERIFICATION_FAILED", "주문을 확인할 수 없습니다.",
                    "주문번호와 조회 토큰을 다시 확인해 주세요.");
        }
    }

    public OrderDetail detailForPayment(String orderNumber) {
        return detail(orderNumber, "1 = ?", 1);
    }

    private OrderDetail detail(String orderNumber, String ownershipPredicate, Object ownerValue) {
        List<OrderRow> rows = jdbcTemplate.query("""
                SELECT o.* FROM orders o WHERE o.order_number = ? AND %s
                """.formatted(ownershipPredicate), (rs, rowNum) -> new OrderRow(
                rs.getObject("id", UUID.class),
                rs.getString("order_number"), rs.getString("order_type"), rs.getString("order_status"),
                rs.getString("payment_status"),
                new OrderApiModels.Buyer(rs.getString("buyer_name"), rs.getString("buyer_email"), rs.getString("buyer_phone")),
                new OrderApiModels.Shipping(rs.getString("recipient_name"), rs.getString("recipient_phone"),
                        rs.getString("postal_code"), rs.getString("address1"), rs.getString("address2"),
                        rs.getString("delivery_message")),
                rs.getLong("items_amount"), rs.getLong("discount_amount"), rs.getLong("shipping_amount"),
                rs.getLong("total_amount"), rs.getString("currency"),
                rs.getTimestamp("ordered_at").toInstant(), nullableInstant(rs.getTimestamp("paid_at"))
        ), orderNumber, ownerValue);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다.",
                    "요청한 주문이 없거나 조회 권한이 없습니다.");
        }
        OrderRow row = rows.getFirst();
        List<OrderItem> items = jdbcTemplate.query("""
                SELECT product_name, brand_name, sku, option_label, unit_price, quantity, line_amount, image_url
                  FROM order_items WHERE order_id = ? ORDER BY created_at, id
                """, (rs, rowNum) -> new OrderItem(rs.getString("product_name"), rs.getString("brand_name"),
                rs.getString("sku"), rs.getString("option_label"), rs.getLong("unit_price"), rs.getInt("quantity"),
                rs.getLong("line_amount"), rs.getString("image_url")), row.id());
        List<PaymentAttempt> payments = jdbcTemplate.query("""
                SELECT id, provider, method, amount, status, approved_at, test_payment
                  FROM payments WHERE order_id = ? ORDER BY created_at
                """, (rs, rowNum) -> new PaymentAttempt(rs.getObject("id", UUID.class), rs.getString("provider"),
                rs.getString("method"), rs.getLong("amount"), rs.getString("status"),
                nullableInstant(rs.getTimestamp("approved_at")), rs.getBoolean("test_payment")), row.id());
        List<StatusHistory> history = jdbcTemplate.query("""
                SELECT from_status, to_status, reason, created_at
                  FROM order_status_history WHERE order_id = ? ORDER BY created_at, id
                """, (rs, rowNum) -> new StatusHistory(rs.getString("from_status"), rs.getString("to_status"),
                rs.getString("reason"), rs.getTimestamp("created_at").toInstant()), row.id());
        return new OrderDetail(row.orderNumber(), row.orderType(), row.orderStatus(), row.paymentStatus(),
                row.buyer(), row.shipping(), items, row.itemsAmount(), row.discountAmount(), row.shippingAmount(),
                row.totalAmount(), row.currency(), payments, history, row.orderedAt(), row.paidAt());
    }

    private List<CartLine> loadPurchasableLines(
            CommerceActor actor,
            List<UUID> requestedIds,
            boolean lockForOrderCreation
    ) {
        List<UUID> distinct = requestedIds.stream().distinct().toList();
        if (distinct.size() != requestedIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "장바구니 항목을 확인해 주세요.",
                    "같은 장바구니 항목을 중복해서 주문할 수 없습니다.");
        }
        List<CartLine> lines = lockForOrderCreation
                ? cartGateway.loadForOrderCreation(actor, requestedIds)
                : cartGateway.load(actor, requestedIds);
        if (lines.size() != requestedIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다.",
                    "선택한 항목이 현재 장바구니에 있는지 확인해 주세요.");
        }
        for (CartLine line : lines) {
            if (!line.available()) {
                throw conflict("PRODUCT_UNAVAILABLE", line.productName() + " 상품은 현재 판매하지 않습니다.");
            }
            if (line.quantity() < 1 || line.quantity() > 10 || line.stockQuantity() < line.quantity()) {
                throw conflict("STOCK_CONFLICT", line.productName() + " 상품의 재고 또는 수량을 확인해 주세요.");
            }
        }
        return lines;
    }

    private String validateOrderType(String requestedType, CommerceActor actor) {
        if (actor.admin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_CUSTOMER_ORDER_FORBIDDEN", "관리자 계정으로 주문할 수 없습니다.",
                    "고객 계정으로 로그인하거나 비회원 세션에서 주문해 주세요.");
        }
        String normalized = requestedType.toUpperCase(Locale.ROOT);
        if (!normalized.equals("MEMBER") && !normalized.equals("GUEST")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "주문 유형을 확인해 주세요.",
                    "orderType은 MEMBER 또는 GUEST여야 합니다.");
        }
        if (normalized.equals("MEMBER") && !actor.isMember()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.",
                    "회원 주문은 로그인 후 이용해 주세요.");
        }
        return normalized;
    }

    private void requireMember(CommerceActor actor) {
        if (!actor.isMember()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.",
                    "회원 주문 내역은 로그인 후 확인할 수 있습니다.");
        }
    }

    private CreateOrderResult replayResult(
            String orderNumber,
            String orderType,
            UUID paymentId,
            UUID idempotencyKey,
            String requestHash
    ) {
        OrderDetail detail = detailForPayment(orderNumber);
        OrderSummary summary = jdbcTemplate.queryForObject("""
                SELECT o.*,
                       (SELECT coalesce(sum(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
                       (SELECT oi.product_name FROM order_items oi WHERE oi.order_id = o.id ORDER BY oi.created_at LIMIT 1) AS representative_item_name
                  FROM orders o WHERE o.order_number = ?
                """, (rs, rowNum) -> summary(rs), orderNumber);
        PaymentAttempt payment = detail.payments().stream().filter(item -> item.paymentAttemptId().equals(paymentId))
                .findFirst().orElseThrow();
        String guestToken = "GUEST".equals(orderType)
                ? guestLookupToken(orderNumber, idempotencyKey, requestHash)
                : null;
        return new CreateOrderResult(true, summary, payment, guestToken);
    }

    private OrderSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderSummary(rs.getString("order_number"), rs.getString("order_type"),
                rs.getString("order_status"), rs.getString("payment_status"), rs.getLong("items_amount"),
                rs.getLong("discount_amount"), rs.getLong("shipping_amount"), rs.getLong("total_amount"),
                rs.getString("currency"), rs.getTimestamp("reservation_expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getInt("item_count"),
                rs.getString("representative_item_name"));
    }

    private String hashRequest(CreateOrderRequest request, CommerceActor actor) {
        String canonical = String.join("\u001f",
                actor.isMember() ? "MEMBER:" + actor.userId() : "VISITOR:" + actor.visitorId(),
                request.orderType().toUpperCase(Locale.ROOT),
                request.cartItemIds().stream().map(UUID::toString).reduce((left, right) -> left + "," + right).orElse(""),
                request.buyer().name(), request.buyer().email().toLowerCase(Locale.ROOT), request.buyer().phone(),
                request.shipping().recipientName(), request.shipping().recipientPhone(), request.shipping().postalCode(),
                request.shipping().address1(), String.valueOf(request.shipping().address2()),
                String.valueOf(request.shipping().deliveryMessage()),
                Boolean.toString(request.agreements().purchaseTermsAccepted()),
                Boolean.toString(request.agreements().privacyCollectionAccepted()));
        return CommerceActorResolver.sha256(canonical);
    }

    private void lockIdempotency(UUID key) {
        long lockKey = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", resultSet -> null, lockKey);
    }

    private long shippingAmount(long itemsAmount) {
        return itemsAmount >= FREE_SHIPPING_THRESHOLD ? 0L : STANDARD_SHIPPING;
    }

    private String newOrderNumber() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        String date = LocalDate.now(clock.withZone(SEOUL)).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "P" + date + "-" + suffix;
    }

    private String guestLookupToken(String orderNumber, UUID idempotencyKey, String requestHash) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(guestLookupTokenSecret, "HmacSHA256"));
            String message = String.join("\u001f", "guest-order-lookup-v1", orderNumber,
                    idempotencyKey.toString(), requestHash);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private byte[] validateGuestLookupTokenSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.order.guest-lookup-token-secret must be configured");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.order.guest-lookup-token-secret must be at least 32 bytes");
        }
        return bytes.clone();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, "주문을 진행할 수 없습니다.", detail);
    }

    private record ExistingOrder(String orderNumber, String orderType, String requestHash, UUID paymentId) {
    }

    private record OrderRow(
            UUID id,
            String orderNumber,
            String orderType,
            String orderStatus,
            String paymentStatus,
            OrderApiModels.Buyer buyer,
            OrderApiModels.Shipping shipping,
            long itemsAmount,
            long discountAmount,
            long shippingAmount,
            long totalAmount,
            String currency,
            Instant orderedAt,
            Instant paidAt
    ) {
    }
}
