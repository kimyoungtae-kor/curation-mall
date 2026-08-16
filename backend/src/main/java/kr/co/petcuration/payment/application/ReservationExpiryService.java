package kr.co.petcuration.payment.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationExpiryService {

    private static final int BATCH_SIZE = 50;
    private final JdbcTemplate jdbcTemplate;

    public ReservationExpiryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${app.order.reservation-expiry-delay:60000}")
    @Transactional
    public void expireReservations() {
        List<UUID> orderIds = jdbcTemplate.query("""
                SELECT o.id
                  FROM orders o
                 WHERE o.order_status = 'PENDING_PAYMENT'
                   AND o.reservation_expires_at <= CURRENT_TIMESTAMP
                   AND EXISTS (
                       SELECT 1 FROM inventory_reservations ir
                        WHERE ir.order_id = o.id AND ir.status = 'ACTIVE'
                   )
                 ORDER BY o.reservation_expires_at, o.id
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), BATCH_SIZE);

        for (UUID orderId : orderIds) {
            expireOrder(orderId);
        }
    }

    private void expireOrder(UUID orderId) {
        List<Reservation> reservations = jdbcTemplate.query("""
                SELECT id, variant_id, quantity
                  FROM inventory_reservations
                 WHERE order_id = ? AND status = 'ACTIVE'
                 ORDER BY variant_id
                 FOR UPDATE
                """, (rs, rowNum) -> new Reservation(
                rs.getObject("id", UUID.class),
                rs.getObject("variant_id", UUID.class),
                rs.getInt("quantity")
        ), orderId);

        for (Reservation reservation : reservations) {
            jdbcTemplate.update("""
                    UPDATE product_variants
                       SET stock_quantity = stock_quantity + ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                     WHERE id = ?
                    """, reservation.quantity(), reservation.variantId());
            jdbcTemplate.update("""
                    UPDATE inventory_reservations
                       SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND status = 'ACTIVE'
                    """, reservation.id());
        }
        if (!reservations.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE payments
                       SET status = 'CANCELLED', failure_code = 'ORDER_RESERVATION_EXPIRED',
                           failure_message = '결제 대기 시간이 만료되었습니다.',
                           cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                     WHERE order_id = ? AND status = 'READY'
                    """, orderId);
            jdbcTemplate.update("""
                    UPDATE orders
                       SET order_status = 'CANCELLED', payment_status = 'CANCELLED',
                           cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
                     WHERE id = ? AND order_status = 'PENDING_PAYMENT'
                    """, orderId);
            jdbcTemplate.update("""
                    INSERT INTO order_status_history (id, order_id, from_status, to_status, reason)
                    VALUES (?, ?, 'PENDING_PAYMENT', 'CANCELLED', '재고 예약 시간 만료')
                    """, UUID.randomUUID(), orderId);
        }
    }

    private record Reservation(UUID id, UUID variantId, int quantity) {
    }
}
