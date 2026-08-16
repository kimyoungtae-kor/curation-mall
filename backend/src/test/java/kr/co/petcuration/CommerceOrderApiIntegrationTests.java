package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommerceOrderApiIntegrationTests {

    private static final String VARIANT_ID = "60000000-0000-0000-0000-000000000001";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void guestOrderRecalculatesPriceReservesStockAndApprovesOnce() throws Exception {
        CsrfState csrf = csrf();
        MvcResult initialCart = mockMvc.perform(get("/api/v1/cart").cookie(csrf.cookie()))
                .andExpect(status().isOk()).andReturn();
        Cookie visitor = requireResponseCookie(initialCart, "PET_VISITOR");
        MvcResult cart = mockMvc.perform(withCsrf(post("/api/v1/cart/items")
                        .cookie(visitor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":2}"), csrf))
                .andExpect(status().isOk()).andReturn();
        String cartItemId = JsonPath.read(cart.getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(withCsrf(post("/api/v1/orders/quote")
                        .cookie(visitor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderType\":\"GUEST\",\"cartItemIds\":[\"" + cartItemId + "\"]}"), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemsAmount").value(56000))
                .andExpect(jsonPath("$.data.shippingAmount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(56000));

        String orderKey = UUID.randomUUID().toString();
        String orderBody = guestOrderBody(cartItemId);
        MvcResult created = mockMvc.perform(withCsrf(post("/api/v1/orders")
                        .cookie(visitor)
                        .header("Idempotency-Key", orderKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody), csrf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order.totalAmount").value(56000))
                .andExpect(jsonPath("$.data.guestLookupToken").isNotEmpty())
                .andReturn();
        String orderNumber = JsonPath.read(created.getResponse().getContentAsString(), "$.data.order.orderNumber");
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.data.guestLookupToken");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT guest_lookup_token_hash FROM orders WHERE order_number = ?", String.class, orderNumber))
                .isNotEqualTo(token)
                .hasSize(64);
        assertThat(jdbcTemplate.queryForObject("SELECT stock_quantity FROM product_variants WHERE id = ?::uuid",
                Integer.class, VARIANT_ID)).isEqualTo(16);

        MvcResult replayed = mockMvc.perform(withCsrf(post("/api/v1/orders")
                        .cookie(visitor)
                        .header("Idempotency-Key", orderKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody), csrf))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.guestLookupToken").value(token))
                .andReturn();
        String replayedToken = JsonPath.read(replayed.getResponse().getContentAsString(),
                "$.data.guestLookupToken");
        assertThat(replayedToken).isEqualTo(token);

        String paymentKey = UUID.randomUUID().toString();
        String paymentBody = """
                {"provider":"SIMULATED","orderNumber":"%s","simulationResult":"APPROVE","amount":56000,"guestLookupToken":"%s"}
                """.formatted(orderNumber, replayedToken);
        mockMvc.perform(withCsrf(post("/api/v1/payments/confirm")
                        .cookie(visitor)
                        .header("Idempotency-Key", paymentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.payment.status").value("APPROVED"));
        mockMvc.perform(withCsrf(post("/api/v1/payments/confirm")
                        .cookie(visitor)
                        .header("Idempotency-Key", paymentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("APPROVED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_reservations WHERE order_id = (SELECT id FROM orders WHERE order_number = ?) AND status = 'COMMITTED'",
                Long.class, orderNumber)).isEqualTo(1L);

        mockMvc.perform(withCsrf(post("/api/v1/guest-orders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"" + orderNumber + "\",\"guestLookupToken\":\""
                                + replayedToken + "\"}"), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
    }

    @Test
    void expiredReservationCancelsPaymentAndReplaysTheSameConflict() throws Exception {
        CsrfState csrf = csrf();
        MvcResult initialCart = mockMvc.perform(get("/api/v1/cart").cookie(csrf.cookie()))
                .andExpect(status().isOk()).andReturn();
        Cookie visitor = requireResponseCookie(initialCart, "PET_VISITOR");
        MvcResult cart = mockMvc.perform(withCsrf(post("/api/v1/cart/items")
                        .cookie(visitor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":1}"), csrf))
                .andExpect(status().isOk()).andReturn();
        String cartItemId = JsonPath.read(cart.getResponse().getContentAsString(), "$.data.items[0].id");
        MvcResult created = mockMvc.perform(withCsrf(post("/api/v1/orders")
                        .cookie(visitor).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(guestOrderBody(cartItemId)), csrf))
                .andExpect(status().isCreated()).andReturn();
        String orderNumber = JsonPath.read(created.getResponse().getContentAsString(), "$.data.order.orderNumber");
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.data.guestLookupToken");
        jdbcTemplate.update("""
                UPDATE orders
                   SET reservation_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                 WHERE order_number = ?
                """, orderNumber);

        String paymentKey = UUID.randomUUID().toString();
        String paymentBody = """
                {"provider":"SIMULATED","orderNumber":"%s","simulationResult":"APPROVE","amount":31000,"guestLookupToken":"%s"}
                """.formatted(orderNumber, token);
        MockHttpServletRequestBuilder confirm = withCsrf(post("/api/v1/payments/confirm")
                .cookie(visitor)
                .header("Idempotency-Key", paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentBody), csrf);
        mockMvc.perform(confirm)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_RESERVATION_EXPIRED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE order_number = ?", String.class, orderNumber))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_status FROM orders WHERE order_number = ?", String.class, orderNumber))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM payments
                 WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)
                """, String.class, orderNumber)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT failure_code FROM payments
                 WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)
                """, String.class, orderNumber)).isEqualTo("ORDER_RESERVATION_EXPIRED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT cancelled_at IS NOT NULL FROM payments
                 WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)
                """, Boolean.class, orderNumber)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM inventory_reservations
                 WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)
                """, String.class, orderNumber)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM order_status_history
                 WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)
                   AND from_status = 'PENDING_PAYMENT' AND to_status = 'CANCELLED'
                   AND reason = '재고 예약 시간 만료'
                """, Long.class, orderNumber)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT stock_quantity FROM product_variants WHERE id = ?::uuid",
                Integer.class, VARIANT_ID)).isEqualTo(18);

        mockMvc.perform(withCsrf(post("/api/v1/payments/confirm")
                        .cookie(visitor)
                        .header("Idempotency-Key", paymentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody), csrf))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_RESERVATION_EXPIRED"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM payment_idempotency_records WHERE idempotency_key = ?::uuid
                """, Long.class, paymentKey)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM order_status_history
                 WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)
                   AND reason = '재고 예약 시간 만료'
                """, Long.class, orderNumber)).isEqualTo(1L);
    }

    @Test
    void failedPaymentRestoresStockAndGuestLookupRequiresToken() throws Exception {
        CsrfState csrf = csrf();
        MvcResult initialCart = mockMvc.perform(get("/api/v1/cart").cookie(csrf.cookie()))
                .andExpect(status().isOk()).andReturn();
        Cookie visitor = requireResponseCookie(initialCart, "PET_VISITOR");
        MvcResult cart = mockMvc.perform(withCsrf(post("/api/v1/cart/items")
                        .cookie(visitor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":1}"), csrf))
                .andExpect(status().isOk()).andReturn();
        String cartItemId = JsonPath.read(cart.getResponse().getContentAsString(), "$.data.items[0].id");
        MvcResult created = mockMvc.perform(withCsrf(post("/api/v1/orders")
                        .cookie(visitor).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(guestOrderBody(cartItemId)), csrf))
                .andExpect(status().isCreated()).andReturn();
        String orderNumber = JsonPath.read(created.getResponse().getContentAsString(), "$.data.order.orderNumber");
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.data.guestLookupToken");

        mockMvc.perform(withCsrf(post("/api/v1/payments/confirm")
                        .cookie(visitor).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"SIMULATED","orderNumber":"%s","simulationResult":"FAIL","amount":31000,"guestLookupToken":"%s"}
                                """.formatted(orderNumber, token)), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.payment.status").value("FAILED"));
        assertThat(jdbcTemplate.queryForObject("SELECT stock_quantity FROM product_variants WHERE id = ?::uuid",
                Integer.class, VARIANT_ID)).isEqualTo(18);

        mockMvc.perform(withCsrf(post("/api/v1/guest-orders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"" + orderNumber + "\",\"guestLookupToken\":\"wrong-token-value\"}"), csrf))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUEST_ORDER_VERIFICATION_FAILED"));
    }

    @Test
    void adminCannotCreateCustomerOrder() throws Exception {
        CsrfState csrf = csrf();
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"DemoPassword123!\"}"), csrf))
                .andExpect(status().isOk()).andReturn();
        Cookie session = requireResponseCookie(login, "SESSION");
        CsrfState authenticatedCsrf = csrf(session);
        mockMvc.perform(withCsrf(post("/api/v1/orders/quote")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderType\":\"MEMBER\",\"cartItemIds\":[\"00000000-0000-0000-0000-000000000001\"]}"), authenticatedCsrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_CUSTOMER_ORDER_FORBIDDEN"));
    }

    private CsrfState csrf(Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (cookies.length > 0) request.cookie(cookies);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        return new CsrfState(requireResponseCookie(result, "XSRF-TOKEN"), token);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfState csrf) {
        return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
    }

    private Cookie requireResponseCookie(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(name + "=")) {
                int end = header.indexOf(';');
                String pair = end < 0 ? header : header.substring(0, end);
                return new Cookie(name, pair.substring(name.length() + 1));
            }
        }
        throw new AssertionError("Missing response cookie: " + name);
    }

    private String guestOrderBody(String cartItemId) {
        return """
                {
                  "orderType":"GUEST",
                  "cartItemIds":["%s"],
                  "buyer":{"name":"비회원","email":"guest@example.com","phone":"01098765432"},
                  "shipping":{"recipientName":"비회원","recipientPhone":"01098765432","postalCode":"06234","address1":"서울시 데모로 1","address2":"101호","deliveryMessage":null},
                  "agreements":{"purchaseTermsAccepted":true,"privacyCollectionAccepted":true}
                }
                """.formatted(cartItemId);
    }

    private record CsrfState(Cookie cookie, String token) {
    }
}
