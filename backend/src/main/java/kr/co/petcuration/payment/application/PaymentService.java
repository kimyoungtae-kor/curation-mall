package kr.co.petcuration.payment.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.order.application.CommerceActor;
import kr.co.petcuration.order.application.CommerceActorResolver;
import kr.co.petcuration.payment.api.PaymentApiModels.ConfirmRequest;
import kr.co.petcuration.payment.api.PaymentApiModels.ConfirmResult;
import kr.co.petcuration.payment.api.PaymentApiModels.PaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final JdbcTemplate jdbcTemplate;
    private final boolean simulatedEnabled;

    public PaymentService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.payment.simulated-enabled:false}") boolean simulatedEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.simulatedEnabled = simulatedEnabled;
    }

    @Transactional(noRollbackFor = PaymentReservationExpiredException.class)
    public ConfirmResult confirm(ConfirmRequest request, UUID idempotencyKey, Optional<CommerceActor> actor) {
        String provider = request.provider().toUpperCase(Locale.ROOT);
        if (!"SIMULATED".equals(provider) || !simulatedEnabled) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_UNSUPPORTED", "지원하지 않는 결제 방식입니다.",
                    "현재 환경에서 사용할 수 있는 테스트 결제 방식을 확인해 주세요.");
        }
        String simulation = request.simulationResult() == null
                ? "" : request.simulationResult().toUpperCase(Locale.ROOT);
        if (!simulation.equals("APPROVE") && !simulation.equals("FAIL")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "테스트 결제 결과를 확인해 주세요.",
                    "simulationResult는 APPROVE 또는 FAIL이어야 합니다.");
        }

        String requestHash = hashRequest(request);
        lockIdempotency(idempotencyKey);
        List<PaymentRow> replayRows = jdbcTemplate.query("""
                SELECT p.*, o.order_number, o.order_status, o.order_type, o.user_id, o.guest_lookup_token_hash,
                       o.total_amount, o.reservation_expires_at, pir.request_hash AS replay_request_hash
                  FROM payment_idempotency_records pir
                  JOIN payments p ON p.id = pir.payment_id
                  JOIN orders o ON o.id = p.order_id
                 WHERE pir.idempotency_key = ?
                """, (rs, rowNum) -> row(rs), idempotencyKey);
        if (!replayRows.isEmpty()) {
            PaymentRow replay = replayRows.getFirst();
            if (!requestHash.equals(replay.replayRequestHash())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "결제 요청이 충돌했습니다.",
                        "같은 멱등성 키가 다른 결제 요청에 사용되었습니다.");
            }
            verifyOwnership(replay, request.guestLookupToken(), actor);
            if (reservationExpired(replay)) {
                throw new PaymentReservationExpiredException();
            }
            return result(replay);
        }

        List<PaymentRow> rows = jdbcTemplate.query("""
                SELECT p.*, o.order_number, o.order_status, o.order_type, o.user_id, o.guest_lookup_token_hash,
                       o.total_amount, o.reservation_expires_at
                  FROM payments p JOIN orders o ON o.id = p.order_id
                 WHERE o.order_number = ?
                 ORDER BY p.created_at DESC
                 LIMIT 1
                 FOR UPDATE OF p, o
                """, (rs, rowNum) -> row(rs), request.orderNumber());
        if (rows.isEmpty()) {
            throw notFound();
        }
        PaymentRow row = rows.getFirst();
        verifyOwnership(row, request.guestLookupToken(), actor);
        if (row.totalAmount() != request.amount()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 일치하지 않습니다.",
                    "화면 금액이 아니라 서버가 계산한 주문 금액으로 다시 시도해 주세요.");
        }
        if (row.status().equals("APPROVED")) {
            recordIdempotency(idempotencyKey, row.paymentId(), requestHash);
            return result(row);
        }
        if (reservationExpired(row)) {
            recordIdempotency(idempotencyKey, row.paymentId(), requestHash);
            throw new PaymentReservationExpiredException();
        }
        if (row.orderStatus().equals("PENDING_PAYMENT") && row.status().equals("READY")
                && !row.reservationExpiresAt().isAfter(Instant.now())) {
            expireReservation(row, idempotencyKey, requestHash);
            throw new PaymentReservationExpiredException();
        }
        if (!row.orderStatus().equals("PENDING_PAYMENT") || !row.status().equals("READY")) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_STATE_CONFLICT", "현재 주문은 결제할 수 없습니다.",
                    "주문 또는 결제 상태를 새로고침해 주세요.");
        }

        if (simulation.equals("APPROVE")) {
            Instant approvedAt = Instant.now();
            jdbcTemplate.update("""
                    UPDATE payments
                       SET provider = 'SIMULATED', method = 'CARD', status = 'APPROVED',
                           idempotency_key = ?, request_hash = ?, approved_at = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                    """, idempotencyKey, requestHash, Timestamp.from(approvedAt), row.paymentId());
            recordIdempotency(idempotencyKey, row.paymentId(), requestHash);
            jdbcTemplate.update("""
                    UPDATE inventory_reservations SET status = 'COMMITTED', updated_at = CURRENT_TIMESTAMP
                     WHERE order_id = ? AND status = 'ACTIVE'
                    """, row.orderId());
            jdbcTemplate.update("""
                    UPDATE orders SET order_status = 'PAID', payment_status = 'APPROVED', paid_at = ?,
                           updated_at = CURRENT_TIMESTAMP, version = version + 1
                     WHERE id = ?
                    """, Timestamp.from(approvedAt), row.orderId());
            addHistory(row.orderId(), "PENDING_PAYMENT", "PAID", "테스트 결제 승인");
            return new ConfirmResult(row.orderNumber(), "PAID",
                    new PaymentResult(row.paymentId(), "SIMULATED", "CARD", "APPROVED", row.totalAmount(),
                            approvedAt, true, null, null), null);
        }

        jdbcTemplate.update("""
                UPDATE payments
                   SET provider = 'SIMULATED', method = 'CARD', status = 'FAILED', idempotency_key = ?, request_hash = ?,
                       failure_code = 'SIMULATED_FAILURE', failure_message = '사용자가 선택한 테스트 실패 결과입니다.',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, idempotencyKey, requestHash, row.paymentId());
        recordIdempotency(idempotencyKey, row.paymentId(), requestHash);
        releaseReservation(row.orderId(), "테스트 결제 실패", "RELEASED");
        jdbcTemplate.update("""
                UPDATE orders SET order_status = 'CANCELLED', payment_status = 'FAILED', cancelled_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP, version = version + 1
                 WHERE id = ?
                """, row.orderId());
        addHistory(row.orderId(), "PENDING_PAYMENT", "CANCELLED", "테스트 결제 실패");
        return new ConfirmResult(row.orderNumber(), "CANCELLED",
                new PaymentResult(row.paymentId(), "SIMULATED", "CARD", "FAILED", row.totalAmount(), null,
                        true, "SIMULATED_FAILURE", "테스트 결제 실패"), null);
    }

    private void releaseReservation(UUID orderId, String reason, String targetStatus) {
        List<Reservation> reservations = jdbcTemplate.query("""
                SELECT id, variant_id, quantity FROM inventory_reservations
                 WHERE order_id = ? AND status = 'ACTIVE' FOR UPDATE
                """, (rs, rowNum) -> new Reservation(rs.getObject("id", UUID.class),
                rs.getObject("variant_id", UUID.class), rs.getInt("quantity")), orderId);
        for (Reservation reservation : reservations) {
            jdbcTemplate.update("""
                    UPDATE product_variants SET stock_quantity = stock_quantity + ?,
                           updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ?
                    """, reservation.quantity(), reservation.variantId());
            jdbcTemplate.update("""
                    UPDATE inventory_reservations SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, targetStatus, reservation.id());
        }
    }

    private void expireReservation(PaymentRow row, UUID idempotencyKey, String requestHash) {
        releaseReservation(row.orderId(), "재고 예약 시간 만료", "EXPIRED");
        jdbcTemplate.update("""
                UPDATE payments
                   SET status = 'CANCELLED', idempotency_key = ?, request_hash = ?,
                       failure_code = 'ORDER_RESERVATION_EXPIRED',
                       failure_message = '결제 대기 시간이 만료되었습니다.',
                       cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'READY'
                """, idempotencyKey, requestHash, row.paymentId());
        int transitioned = jdbcTemplate.update("""
                UPDATE orders
                   SET order_status = 'CANCELLED', payment_status = 'CANCELLED',
                       cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
                 WHERE id = ? AND order_status = 'PENDING_PAYMENT'
                """, row.orderId());
        if (transitioned == 1) {
            addHistory(row.orderId(), "PENDING_PAYMENT", "CANCELLED", "재고 예약 시간 만료");
        }
        recordIdempotency(idempotencyKey, row.paymentId(), requestHash);
    }

    private boolean reservationExpired(PaymentRow row) {
        return "ORDER_RESERVATION_EXPIRED".equals(row.failureCode());
    }

    private void verifyOwnership(PaymentRow row, String guestToken, Optional<CommerceActor> actor) {
        if (row.orderType().equals("MEMBER")) {
            if (actor.isEmpty() || !actor.get().isMember() || !row.userId().equals(actor.get().userId())) {
                throw notFound();
            }
            return;
        }
        if (guestToken == null || guestToken.isBlank()
                || !row.guestTokenHash().equals(CommerceActorResolver.sha256(guestToken))) {
            throw notFound();
        }
    }

    private ConfirmResult result(PaymentRow row) {
        return new ConfirmResult(row.orderNumber(), row.orderStatus(),
                new PaymentResult(row.paymentId(), row.provider(), row.method(), row.status(), row.totalAmount(),
                        row.approvedAt(), row.testPayment(), row.failureCode(), row.failureMessage()), null);
    }

    private PaymentRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        return new PaymentRow(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getString("order_number"), rs.getString("order_type"), rs.getObject("user_id", UUID.class),
                rs.getString("guest_lookup_token_hash"), rs.getString("order_status"), rs.getString("status"),
                rs.getString("provider"), rs.getString("method"), rs.getLong("total_amount"),
                rs.getTimestamp("reservation_expires_at").toInstant(), rs.getString("request_hash"),
                hasColumn(rs, "replay_request_hash") ? rs.getString("replay_request_hash") : null,
                approvedAt == null ? null : approvedAt.toInstant(), rs.getBoolean("test_payment"),
                rs.getString("failure_code"), rs.getString("failure_message"));
    }

    private String hashRequest(ConfirmRequest request) {
        return CommerceActorResolver.sha256(String.join("\u001f",
                request.provider().toUpperCase(Locale.ROOT), request.orderNumber(),
                String.valueOf(request.paymentKey()), String.valueOf(request.simulationResult()),
                Long.toString(request.amount()), String.valueOf(request.guestLookupToken())));
    }

    private void recordIdempotency(UUID key, UUID paymentId, String requestHash) {
        jdbcTemplate.update("""
                INSERT INTO payment_idempotency_records (idempotency_key, payment_id, request_hash)
                VALUES (?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, key, paymentId, requestHash);
    }

    private boolean hasColumn(java.sql.ResultSet resultSet, String columnName) throws java.sql.SQLException {
        java.sql.ResultSetMetaData metadata = resultSet.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            if (columnName.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private void lockIdempotency(UUID key) {
        long lockKey = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", resultSet -> null, lockKey);
    }

    private void addHistory(UUID orderId, String from, String to, String reason) {
        jdbcTemplate.update("""
                INSERT INTO order_status_history (id, order_id, from_status, to_status, reason)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, from, to, reason);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "결제할 주문을 찾을 수 없습니다.",
                "주문 정보와 조회 권한을 확인해 주세요.");
    }

    private record Reservation(UUID id, UUID variantId, int quantity) {
    }

    private record PaymentRow(
            UUID paymentId,
            UUID orderId,
            String orderNumber,
            String orderType,
            UUID userId,
            String guestTokenHash,
            String orderStatus,
            String status,
            String provider,
            String method,
            long totalAmount,
            Instant reservationExpiresAt,
            String requestHash,
            String replayRequestHash,
            Instant approvedAt,
            boolean testPayment,
            String failureCode,
            String failureMessage
    ) {
    }
}
