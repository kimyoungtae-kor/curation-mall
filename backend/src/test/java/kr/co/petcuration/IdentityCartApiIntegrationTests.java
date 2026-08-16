package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentityCartApiIntegrationTests {

    private static final String VARIANT_ID = "60000000-0000-0000-0000-000000000001";
    private static final String PRODUCT_ID = "50000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void csrfIsPublicAndRequiredForStateChangingAuthenticationRequests() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("csrf-test@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void signupPersistsBcryptPasswordCreatesSessionAndLogoutInvalidatesIt() throws Exception {
        CsrfState csrf = csrf();
        MvcResult signup = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(signupBody("new-member@example.com")),
                        csrf
                ))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.email").value("new-member@example.com"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("CUSTOMER"))
                .andReturn();

        Cookie session = requireResponseCookie(signup, "SESSION");
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE normalized_email = ?",
                String.class,
                "new-member@example.com"
        );
        assertThat(storedHash).doesNotContain("DemoPassword123!");
        assertThat(passwordEncoder.matches("DemoPassword123!", storedHash)).isTrue();

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.email").value("new-member@example.com"));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        CsrfState authenticatedCsrf = csrf(session);
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout").cookie(session), authenticatedCsrf))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    void signupDiscardsGuestCartAndLegacyWishlistWithoutAttachingThemToNewMember() throws Exception {
        CsrfState guestCsrf = csrf();
        MvcResult guestCart = mockMvc.perform(get("/api/v1/cart").cookie(guestCsrf.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        String guestCartId = JsonPath.read(guestCart.getResponse().getContentAsString(), "$.data.id");
        Cookie visitor = requireResponseCookie(guestCart, "PET_VISITOR");
        mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":3}"),
                        guestCsrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(3));

        String visitorId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM visitors WHERE token_hash = ?",
                String.class,
                sha256(visitor.getValue())
        );
        jdbcTemplate.update("""
                INSERT INTO wishlist_items (id, visitor_id, product_id, created_at)
                VALUES ('71000000-0000-0000-0000-000000000001', ?::uuid, ?::uuid, CURRENT_TIMESTAMP)
                """, visitorId, PRODUCT_ID);

        MvcResult signup = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/signup")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(signupBody("empty-member@example.com")),
                        guestCsrf
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mergeResult.merged").value(false))
                .andExpect(jsonPath("$.data.mergeResult.cartItemCount").value(0))
                .andExpect(jsonPath("$.data.mergeResult.wishlistCount").value(0))
                .andReturn();
        Cookie memberSession = requireResponseCookie(signup, "SESSION");

        mockMvc.perform(get("/api/v1/cart").cookie(memberSession, visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/v1/wishlist").cookie(memberSession, visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        String userId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM users WHERE normalized_email = 'empty-member@example.com'",
                String.class
        );
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(ci.quantity), 0)
                  FROM carts c
                  LEFT JOIN cart_items ci ON ci.cart_id = c.id
                 WHERE c.user_id = ?::uuid AND c.status = 'ACTIVE'
                """, Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wishlist_items WHERE user_id = ?::uuid",
                Long.class,
                userId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM carts WHERE id = ?::uuid",
                String.class,
                guestCartId
        )).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wishlist_items WHERE visitor_id = ?::uuid",
                Long.class,
                visitorId
        )).isZero();

        CsrfState memberCsrf = csrf(memberSession, visitor);
        mockMvc.perform(withCsrf(
                        post("/api/v1/auth/logout").cookie(memberSession, visitor),
                        memberCsrf
                ))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").cookie(visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.cartCount").value(0))
                .andExpect(jsonPath("$.data.wishlistCount").value(0));
        MvcResult freshGuestCart = mockMvc.perform(get("/api/v1/cart").cookie(visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andReturn();
        String freshGuestCartId = JsonPath.read(
                freshGuestCart.getResponse().getContentAsString(),
                "$.data.id"
        );
        assertThat(freshGuestCartId).isNotEqualTo(guestCartId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(sum(quantity), 0) FROM cart_items WHERE cart_id = ?::uuid",
                Integer.class,
                guestCartId
        )).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedSignupPreservesGuestCartAndLegacyWishlistButAnonymousSnapshotHidesWishlistCount() throws Exception {
        CsrfState guestCsrf = csrf();
        MvcResult guestCart = mockMvc.perform(get("/api/v1/cart").cookie(guestCsrf.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie visitor = requireResponseCookie(guestCart, "PET_VISITOR");
        mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":1}"),
                        guestCsrf
                ))
                .andExpect(status().isOk());
        String visitorId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM visitors WHERE token_hash = ?",
                String.class,
                sha256(visitor.getValue())
        );
        jdbcTemplate.update("""
                INSERT INTO wishlist_items (id, visitor_id, product_id, created_at)
                VALUES ('71000000-0000-0000-0000-000000000002', ?::uuid, ?::uuid, CURRENT_TIMESTAMP)
                """, visitorId, PRODUCT_ID);

        mockMvc.perform(withCsrf(
                        post("/api/v1/auth/signup")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(signupBody("demo@example.com")),
                        guestCsrf
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(ci.quantity), 0)
                  FROM carts c
                  JOIN cart_items ci ON ci.cart_id = c.id
                 WHERE c.visitor_id = ?::uuid AND c.status = 'ACTIVE'
                """, Integer.class, visitorId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wishlist_items WHERE visitor_id = ?::uuid",
                Long.class,
                visitorId
        )).isEqualTo(1L);
        mockMvc.perform(get("/api/v1/auth/me").cookie(visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.cartCount").value(1))
                .andExpect(jsonPath("$.data.wishlistCount").value(0));
    }

    @Test
    void differentMemberIdsCannotReadOrChangeEachOthersCartAndWishlist() throws Exception {
        CsrfState firstSignupCsrf = csrf();
        MvcResult firstSignup = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(signupBody("member-a@example.com")),
                        firstSignupCsrf
                ))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie firstSession = requireResponseCookie(firstSignup, "SESSION");
        CsrfState firstMemberCsrf = csrf(firstSession);
        MvcResult firstCart = mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(firstSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":2}"),
                        firstMemberCsrf
                ))
                .andExpect(status().isOk())
                .andReturn();
        String firstCartItemId = JsonPath.read(
                firstCart.getResponse().getContentAsString(),
                "$.data.items[0].id"
        );
        mockMvc.perform(withCsrf(
                        post("/api/v1/wishlist/" + PRODUCT_ID).cookie(firstSession),
                        firstMemberCsrf
                ))
                .andExpect(status().isOk());

        CsrfState secondSignupCsrf = csrf();
        MvcResult secondSignup = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(signupBody("member-b@example.com")),
                        secondSignupCsrf
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mergeResult.cartItemCount").value(0))
                .andExpect(jsonPath("$.data.mergeResult.wishlistCount").value(0))
                .andReturn();
        Cookie secondSession = requireResponseCookie(secondSignup, "SESSION");
        CsrfState secondMemberCsrf = csrf(secondSession);

        mockMvc.perform(get("/api/v1/cart").cookie(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/v1/wishlist").cookie(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(withCsrf(
                        patch("/api/v1/cart/items/" + firstCartItemId)
                                .cookie(secondSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":4}"),
                        secondMemberCsrf
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(withCsrf(
                        delete("/api/v1/wishlist/" + PRODUCT_ID).cookie(secondSession),
                        secondMemberCsrf
                ))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(2));
        mockMvc.perform(get("/api/v1/wishlist").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        String firstUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM users WHERE normalized_email = 'member-a@example.com'",
                String.class
        );
        String secondUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM users WHERE normalized_email = 'member-b@example.com'",
                String.class
        );
        assertThat(firstUserId).isNotEqualTo(secondUserId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wishlist_items WHERE user_id = ?::uuid",
                Long.class,
                firstUserId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wishlist_items WHERE user_id = ?::uuid",
                Long.class,
                secondUserId
        )).isZero();
    }

    @Test
    void visitorCartPersistsAndIsIsolatedFromAnotherVisitor() throws Exception {
        CsrfState csrf = csrf();
        MvcResult firstCart = mockMvc.perform(get("/api/v1/cart").cookie(csrf.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andReturn();
        Cookie visitorA = requireResponseCookie(firstCart, "PET_VISITOR");
        assertThat(firstCart.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(value -> value.startsWith("PET_VISITOR=") && value.contains("HttpOnly"))).isTrue();

        MvcResult added = mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(visitorA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":2}"),
                        csrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.items[0].currentUnitPrice").value(28000))
                .andReturn();
        String itemId = JsonPath.read(added.getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(get("/api/v1/cart").cookie(visitorA, csrf.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(2));

        MvcResult secondCart = mockMvc.perform(get("/api/v1/cart").cookie(csrf.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andReturn();
        Cookie visitorB = requireResponseCookie(secondCart, "PET_VISITOR");
        assertThat(visitorB.getValue()).isNotEqualTo(visitorA.getValue());

        mockMvc.perform(withCsrf(
                        patch("/api/v1/cart/items/" + itemId)
                                .cookie(visitorB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3}"),
                        csrf
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        String firstCartId = JsonPath.read(firstCart.getResponse().getContentAsString(), "$.data.id");
        String tokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM visitors WHERE id = (SELECT visitor_id FROM carts WHERE status = 'ACTIVE' AND id = ?::uuid)",
                String.class,
                firstCartId
        );
        assertThat(tokenHash).hasSize(64).isNotEqualTo(visitorA.getValue());
    }

    @Test
    void loginMergesGuestDataOnceAndCapsCombinedQuantity() throws Exception {
        CsrfState memberCsrf = csrf();
        MvcResult memberLogin = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody()),
                        memberCsrf
                ))
                .andExpect(status().isOk())
                .andReturn();
        Cookie initialMemberSession = requireResponseCookie(memberLogin, "SESSION");
        CsrfState refreshedMemberCsrf = csrf(initialMemberSession);
        mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(initialMemberSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":7}"),
                        refreshedMemberCsrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(7));

        CsrfState guestCsrf = csrf();
        MvcResult guestCart = mockMvc.perform(get("/api/v1/cart").cookie(guestCsrf.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie visitor = requireResponseCookie(guestCart, "PET_VISITOR");
        mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":5}"),
                        guestCsrf
                ))
                .andExpect(status().isOk());
        String visitorId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM visitors WHERE token_hash = ?",
                String.class,
                sha256(visitor.getValue())
        );
        jdbcTemplate.update("""
                INSERT INTO wishlist_items (id, visitor_id, product_id, created_at)
                VALUES ('71000000-0000-0000-0000-000000000003', ?::uuid, ?::uuid, CURRENT_TIMESTAMP)
                """, visitorId, PRODUCT_ID);
        MvcResult mergeLogin = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/login")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody()),
                        guestCsrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mergeResult.merged").value(true))
                .andExpect(jsonPath("$.data.mergeResult.cartItemCount").value(10))
                .andExpect(jsonPath("$.data.mergeResult.wishlistCount").value(0))
                .andExpect(jsonPath("$.data.mergeResult.adjustments[0].mergedQuantity").value(10))
                .andExpect(jsonPath("$.data.mergeResult.adjustments[0].reason").value("PURCHASE_LIMIT"))
                .andReturn();
        Cookie mergedSession = requireResponseCookie(mergeLogin, "SESSION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE visitor_id = (SELECT id FROM visitors WHERE token_hash = ?) AND status = 'MERGED'",
                Long.class,
                sha256(visitor.getValue())
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wishlist_items WHERE visitor_id = ?::uuid",
                Long.class,
                visitorId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM wishlist_items
                 WHERE user_id = (SELECT id FROM users WHERE normalized_email = 'demo@example.com')
                """, Long.class)).isZero();

        CsrfState mergedCsrf = csrf(mergedSession, visitor);
        mockMvc.perform(withCsrf(
                        post("/api/v1/auth/merge-guest-data").cookie(mergedSession, visitor),
                        mergedCsrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merged").value(false))
                .andExpect(jsonPath("$.data.cartItemCount").value(10))
                .andExpect(jsonPath("$.data.wishlistCount").value(0));
    }

    @Test
    void adminLoginDoesNotConsumeVisitorCart() throws Exception {
        CsrfState guestCsrf = csrf();
        MvcResult guestCart = mockMvc.perform(get("/api/v1/cart").cookie(guestCsrf.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie visitor = requireResponseCookie(guestCart, "PET_VISITOR");

        mockMvc.perform(withCsrf(
                        post("/api/v1/cart/items")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"variantId\":\"" + VARIANT_ID + "\",\"quantity\":2}"),
                        guestCsrf
                ))
                .andExpect(status().isOk());
        jdbcTemplate.update("""
                INSERT INTO wishlist_items (id, user_id, product_id, created_at)
                VALUES (
                    '71000000-0000-0000-0000-000000000004',
                    (SELECT id FROM users WHERE normalized_email = 'admin@example.com'),
                    ?::uuid,
                    CURRENT_TIMESTAMP
                )
                """, PRODUCT_ID);
        MvcResult adminLogin = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/login")
                                .cookie(visitor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"admin@example.com\",\"password\":\"DemoPassword123!\"}"),
                        guestCsrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.mergeResult.merged").value(false))
                .andExpect(jsonPath("$.data.mergeResult.cartItemCount").value(0))
                .andExpect(jsonPath("$.data.mergeResult.wishlistCount").value(0))
                .andReturn();

        String visitorHash = sha256(visitor.getValue());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM carts
                 WHERE visitor_id = (SELECT id FROM visitors WHERE token_hash = ?)
                   AND status = 'ACTIVE'
                """, Long.class, visitorHash)).isEqualTo(1L);
        Cookie adminSession = requireResponseCookie(adminLogin, "SESSION");
        mockMvc.perform(get("/api/v1/auth/me").cookie(adminSession, visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(0));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM wishlist_items
                 WHERE user_id = (SELECT id FROM users WHERE normalized_email = 'admin@example.com')
                """, Long.class)).isEqualTo(1L);
        CsrfState adminCsrf = csrf(adminSession, visitor);
        mockMvc.perform(withCsrf(
                        post("/api/v1/auth/merge-guest-data").cookie(adminSession, visitor),
                        adminCsrf
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_GUEST_MERGE_FORBIDDEN"));
    }

    @Test
    void wishlistRequiresCustomerMembership() throws Exception {
        CsrfState anonymousCsrf = csrf();
        mockMvc.perform(get("/api/v1/wishlist").cookie(anonymousCsrf.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(withCsrf(post("/api/v1/wishlist/" + PRODUCT_ID), anonymousCsrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(withCsrf(delete("/api/v1/wishlist/" + PRODUCT_ID), anonymousCsrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        MvcResult adminLogin = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"admin@example.com\",\"password\":\"DemoPassword123!\"}"),
                        anonymousCsrf
                ))
                .andExpect(status().isOk())
                .andReturn();
        Cookie adminSession = requireResponseCookie(adminLogin, "SESSION");
        CsrfState adminCsrf = csrf(adminSession);
        mockMvc.perform(get("/api/v1/wishlist").cookie(adminSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(withCsrf(
                        post("/api/v1/wishlist/" + PRODUCT_ID).cookie(adminSession),
                        adminCsrf
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(withCsrf(
                        delete("/api/v1/wishlist/" + PRODUCT_ID).cookie(adminSession),
                        adminCsrf
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        CsrfState customerLoginCsrf = csrf();
        MvcResult customerLogin = mockMvc.perform(withCsrf(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody()),
                        customerLoginCsrf
                ))
                .andExpect(status().isOk())
                .andReturn();
        Cookie customerSession = requireResponseCookie(customerLogin, "SESSION");
        CsrfState customerCsrf = csrf(customerSession);
        mockMvc.perform(withCsrf(
                        post("/api/v1/wishlist/" + PRODUCT_ID).cookie(customerSession),
                        customerCsrf
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wishlisted").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(1));
        mockMvc.perform(get("/api/v1/wishlist").cookie(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(withCsrf(
                        delete("/api/v1/wishlist/" + PRODUCT_ID).cookie(customerSession),
                        customerCsrf
                ))
                .andExpect(status().isNoContent());
    }

    @Test
    void signupRejectsEmailThatCannotFitTheSessionPrincipalIndex() throws Exception {
        String tooLongEmail = "a".repeat(89) + "@example.com";
        assertThat(tooLongEmail).hasSize(101);
        CsrfState csrf = csrf();

        mockMvc.perform(withCsrf(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(signupBody(tooLongEmail)),
                        csrf
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE normalized_email = ?",
                Long.class,
                tooLongEmail
        )).isZero();
    }

    @Test
    void invalidCredentialsDoNotRevealWhetherEmailExists() throws Exception {
        CsrfState csrf = csrf();
        for (String email : List.of("demo@example.com", "missing@example.com")) {
            mockMvc.perform(withCsrf(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"),
                            csrf
                    ))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("이메일 또는 비밀번호가 올바르지 않습니다."));
        }
    }

    private CsrfState csrf(Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
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

    private String signupBody(String email) {
        return """
                {
                  "email": "%s",
                  "password": "DemoPassword123!",
                  "name": "테스트회원",
                  "phone": "01012345678",
                  "requiredTermsAccepted": true
                }
                """.formatted(email);
    }

    private String loginBody() {
        return """
                {"email":"demo@example.com","password":"DemoPassword123!"}
                """;
    }

    private String sha256(String value) {
        return kr.co.petcuration.order.application.CommerceActorResolver.sha256(value);
    }

    private record CsrfState(Cookie cookie, String token) {
    }
}
