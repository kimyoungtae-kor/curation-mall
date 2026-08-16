package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.order.api.OrderApiModels.Agreements;
import kr.co.petcuration.order.api.OrderApiModels.Buyer;
import kr.co.petcuration.order.api.OrderApiModels.CreateOrderRequest;
import kr.co.petcuration.order.api.OrderApiModels.CreateOrderResult;
import kr.co.petcuration.order.api.OrderApiModels.Shipping;
import kr.co.petcuration.order.application.CommerceActor;
import kr.co.petcuration.order.application.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderCartConcurrencyIntegrationTests {

    private static final UUID VARIANT_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Autowired
    OrderService orderService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void sameCartItemCanOnlyCreateOneOrderAcrossDifferentIdempotencyKeys() throws Exception {
        UUID visitorId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID cartItemId = UUID.randomUUID();
        int initialStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product_variants WHERE id = ?",
                Integer.class,
                VARIANT_ID
        );
        long initialOrderCount = count("orders");
        long initialReservationCount = count("inventory_reservations");

        jdbcTemplate.update("""
                INSERT INTO visitors (id, token_hash, expires_at, last_seen_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP)
                """, visitorId, "concurrency-visitor-" + visitorId);
        jdbcTemplate.update("INSERT INTO carts (id, visitor_id, status) VALUES (?, ?, 'ACTIVE')", cartId, visitorId);
        jdbcTemplate.update("""
                INSERT INTO cart_items (id, cart_id, variant_id, quantity, unit_price_at_add)
                VALUES (?, ?, ?, 1, 28000)
                """, cartItemId, cartId, VARIANT_ID);

        CommerceActor actor = CommerceActor.visitor(visitorId);
        CreateOrderRequest request = request(cartItemId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        UUID firstIdempotencyKey = UUID.randomUUID();
        UUID secondIdempotencyKey = UUID.randomUUID();
        assertThat(firstIdempotencyKey).isNotEqualTo(secondIdempotencyKey);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executor.submit(() ->
                    createWhenReleased(request, actor, firstIdempotencyKey, ready, start));
            Future<Attempt> second = executor.submit(() ->
                    createWhenReleased(request, actor, secondIdempotencyKey, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(Attempt::success).hasSize(1);
            assertThat(results).filteredOn(result -> !result.success()).singleElement().satisfies(result -> {
                assertThat(result.status()).isBetween(400, 499);
                assertThat(result.code()).isIn("CART_ITEM_NOT_FOUND", "CART_CHANGED");
            });
        }

        assertThat(count("orders") - initialOrderCount).isEqualTo(1);
        assertThat(count("inventory_reservations") - initialReservationCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product_variants WHERE id = ?",
                Integer.class,
                VARIANT_ID
        )).isEqualTo(initialStock - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_items WHERE cart_item_id = ?",
                Long.class,
                cartItemId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cart_items WHERE id = ?",
                Long.class,
                cartItemId
        )).isZero();
    }

    private Attempt createWhenReleased(
            CreateOrderRequest request,
            CommerceActor actor,
            UUID idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return Attempt.failure(500, "TEST_TIMEOUT");
            }
            CreateOrderResult result = orderService.create(request, idempotencyKey, actor);
            return Attempt.success(result.order().orderNumber());
        } catch (ApiException exception) {
            return Attempt.failure(exception.getStatus().value(), exception.getCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Attempt.failure(500, "TEST_INTERRUPTED");
        }
    }

    private CreateOrderRequest request(UUID cartItemId) {
        return new CreateOrderRequest(
                "GUEST",
                List.of(cartItemId),
                new Buyer("동시 주문", "concurrency@example.com", "01012345678"),
                new Shipping("동시 주문", "01012345678", "06234", "서울시 테스트로 1", "101호", null),
                new Agreements(true, true)
        );
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private record Attempt(boolean success, int status, String code, String orderNumber) {

        static Attempt success(String orderNumber) {
            return new Attempt(true, 201, null, orderNumber);
        }

        static Attempt failure(int status, String code) {
            return new Attempt(false, status, code, null);
        }
    }
}
