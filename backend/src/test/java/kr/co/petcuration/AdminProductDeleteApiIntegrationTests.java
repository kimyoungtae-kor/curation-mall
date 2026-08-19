package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import kr.co.petcuration.admin.api.AdminApiModels.HeroSlideUpdate;
import kr.co.petcuration.admin.application.AdminService;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.common.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminProductDeleteApiIntegrationTests {

    private static final UUID ADMIN_ID = UUID.fromString("11000000-0000-0000-0000-000000000002");
    private static final UUID BRAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("40000000-0000-0000-0000-000000000005");
    private static final UUID SPECIES_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID COLLECTION_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final String IMAGE_KEY = "demo/catalog/oasis-water-bowl.webp";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StorageService storageService;

    @Autowired
    AdminService adminService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void deletesEligibleProductAndDependentMetadataButKeepsPhysicalImageAndAuditSnapshot() throws Exception {
        TestProduct product = createProduct("HIDDEN", 0);
        UUID visitorId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO visitors (id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, visitorId, UUID.randomUUID().toString(), OffsetDateTime.now().plusDays(1));
        jdbcTemplate.update("INSERT INTO carts (id, visitor_id, status) VALUES (?, ?, 'ACTIVE')", cartId, visitorId);
        jdbcTemplate.update("""
                INSERT INTO cart_items (id, cart_id, variant_id, quantity, unit_price_at_add)
                VALUES (?, ?, ?, 1, 19000)
                """, UUID.randomUUID(), cartId, product.variantId());
        jdbcTemplate.update("""
                INSERT INTO wishlist_items (id, visitor_id, product_id)
                VALUES (?, ?, ?)
                """, UUID.randomUUID(), visitorId, product.id());
        jdbcTemplate.update("""
                INSERT INTO product_images (id, product_id, storage_key, alt_text, sort_order)
                VALUES (?, ?, ?, '삭제 테스트 이미지', 1)
                """, UUID.randomUUID(), product.id(), IMAGE_KEY);
        jdbcTemplate.update("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)",
                product.id(), CATEGORY_ID);
        jdbcTemplate.update("INSERT INTO product_species (product_id, species_id) VALUES (?, ?)",
                product.id(), SPECIES_ID);
        jdbcTemplate.update("""
                INSERT INTO collection_products (id, collection_id, product_id, sort_order)
                VALUES (?, ?, ?, 999)
                """, UUID.randomUUID(), COLLECTION_ID, product.id());
        assertThat(storageService.find(IMAGE_KEY)).isPresent();

        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0").cookie(admin.session()), admin.csrf()))
                .andExpect(status().isNoContent());

        assertThat(count("products", "id", product.id())).isZero();
        assertThat(count("product_variants", "product_id", product.id())).isZero();
        assertThat(count("product_images", "product_id", product.id())).isZero();
        assertThat(count("product_categories", "product_id", product.id())).isZero();
        assertThat(count("product_species", "product_id", product.id())).isZero();
        assertThat(count("collection_products", "product_id", product.id())).isZero();
        assertThat(count("wishlist_items", "product_id", product.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cart_items WHERE variant_id = ?",
                Long.class, product.variantId())).isZero();
        assertThat(storageService.find(IMAGE_KEY)).isPresent();

        mockMvc.perform(get("/api/v1/admin/products/{id}", product.id()).cookie(admin.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/catalog/products/{slug}", product.slug()))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM admin_audit_logs
                 WHERE admin_user_id = ?
                   AND action = 'PRODUCT_DELETE'
                   AND resource_id = ?
                   AND before_summary ->> 'name' = ?
                   AND (before_summary ->> 'variantCount')::integer = 1
                   AND (before_summary ->> 'imageCount')::integer = 1
                """, Long.class, ADMIN_ID, product.id().toString(), product.name())).isOne();
    }

    @Test
    void orderHistoryRequiresExplicitConfirmationThenDetachesCurrentCatalogAndPreservesSnapshots() throws Exception {
        TestProduct product = createProduct("DISCONTINUED", 0);
        UUID orderId = jdbcTemplate.queryForObject("SELECT id FROM orders ORDER BY created_at LIMIT 1", UUID.class);
        String orderNumber = jdbcTemplate.queryForObject("SELECT order_number FROM orders WHERE id = ?",
                String.class, orderId);
        long orderCount = jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
        long paymentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Long.class);
        UUID orderItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, product_name, brand_name, sku,
                    option_label, unit_price, quantity, line_amount
                ) VALUES (?, ?, ?, ?, ?, '테스트 브랜드', ?, '기본', 19000, 1, 19000)
                """, orderItemId, orderId, product.id(), product.variantId(), product.name(), product.sku());
        jdbcTemplate.update("""
                INSERT INTO inventory_reservations (
                    id, order_id, variant_id, quantity, status, expires_at
                ) VALUES (?, ?, ?, 1, 'COMMITTED', CURRENT_TIMESTAMP)
                """, reservationId, orderId, product.variantId());

        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0").cookie(admin.session()), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ORDER_HISTORY"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("한 번 더 확인")));

        assertThat(count("products", "id", product.id())).isOne();
        assertThat(count("product_variants", "product_id", product.id())).isOne();
        assertThat(count("order_items", "product_id", product.id())).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM admin_audit_logs
                 WHERE action = 'PRODUCT_DELETE' AND resource_id = ?
                """, Long.class, product.id().toString())).isZero();

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0")
                        .param("confirmOrderHistory", "true")
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isNoContent());

        assertThat(count("products", "id", product.id())).isZero();
        assertThat(count("product_variants", "product_id", product.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(orderCount);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Long.class)).isEqualTo(paymentCount);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_items WHERE id = ?",
                Long.class, orderItemId)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT product_id IS NULL AND variant_id IS NULL FROM order_items WHERE id = ?",
                Boolean.class, orderItemId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT product_name FROM order_items WHERE id = ?",
                String.class, orderItemId)).isEqualTo(product.name());
        assertThat(jdbcTemplate.queryForObject("SELECT sku FROM order_items WHERE id = ?",
                String.class, orderItemId)).isEqualTo(product.sku());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_reservations WHERE id = ?",
                Long.class, reservationId)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT variant_id IS NULL FROM inventory_reservations WHERE id = ?",
                Boolean.class, reservationId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM admin_audit_logs
                 WHERE action = 'PRODUCT_DELETE'
                   AND resource_id = ?
                   AND (before_summary ->> 'orderHistoryConfirmed')::boolean
                   AND (before_summary ->> 'orderItemCount')::integer = 1
                   AND (before_summary ->> 'reservationCount')::integer = 1
                """, Long.class, product.id().toString())).isOne();

        mockMvc.perform(get("/api/v1/admin/orders/{orderNumber}", orderNumber).cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(product.name())));
    }

    @Test
    void activeInventoryReservationBlocksEvenConfirmedHistoryDeletion() throws Exception {
        TestProduct product = createProduct("DISCONTINUED", 0);
        UUID orderId = jdbcTemplate.queryForObject("SELECT id FROM orders ORDER BY created_at LIMIT 1", UUID.class);
        jdbcTemplate.update("""
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, product_name, brand_name, sku,
                    option_label, unit_price, quantity, line_amount
                ) VALUES (?, ?, ?, ?, ?, '테스트 브랜드', ?, '기본', 19000, 1, 19000)
                """, UUID.randomUUID(), orderId, product.id(), product.variantId(), product.name(), product.sku());
        UUID reservationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_reservations (
                    id, order_id, variant_id, quantity, status, expires_at
                ) VALUES (?, ?, ?, 1, 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '20 minutes')
                """, reservationId, orderId, product.variantId());

        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0")
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ACTIVE_RESERVATION"));

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0")
                        .param("confirmOrderHistory", "true")
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ACTIVE_RESERVATION"));

        assertThat(count("products", "id", product.id())).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT variant_id FROM inventory_reservations WHERE id = ?",
                UUID.class, reservationId)).isEqualTo(product.variantId());
    }

    @Test
    void staleVersionAndMissingProductAreReportedWithoutDeletingCurrentData() throws Exception {
        TestProduct product = createProduct("HIDDEN", 2);
        AuthState admin = login("admin@example.com");

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "1").cookie(admin.session()), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
        assertThat(count("products", "id", product.id())).isOne();

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", UUID.randomUUID())
                        .param("version", "0").cookie(admin.session()), admin.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void publishedProductMustBeHiddenOrDiscontinuedBeforeDeletion() throws Exception {
        TestProduct product = createProduct("PUBLISHED", 0);
        AuthState admin = login("admin@example.com");

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0").cookie(admin.session()), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_MUST_BE_UNPUBLISHED"));
        assertThat(count("products", "id", product.id())).isOne();
    }

    @Test
    void everyHomeProductLinkShapeBlocksDeletion() throws Exception {
        TestProduct heroProduct = createProduct("HIDDEN", 0);
        TestProduct lifestyleProduct = createProduct("HIDDEN", 0);
        TestProduct announcementProduct = createProduct("HIDDEN", 0);
        jdbcTemplate.update("""
                UPDATE home_hero_slides SET link_type = 'PRODUCT', link_value = ?
                 WHERE id = (SELECT id FROM home_hero_slides ORDER BY id LIMIT 1)
                """, heroProduct.slug());
        jdbcTemplate.update("""
                UPDATE home_lifestyle_contents SET link_type = 'PRODUCT', link_value = ?
                 WHERE id = (SELECT id FROM home_lifestyle_contents ORDER BY id LIMIT 1)
                """, lifestyleProduct.slug());
        jdbcTemplate.update("""
                UPDATE home_sections
                   SET content = jsonb_build_object(
                       'announcementText', '안내', 'linkType', 'PRODUCT', 'linkValue', ?
                   )
                 WHERE section_key = 'ANNOUNCEMENT_HEADER'
                """, "  " + announcementProduct.slug() + "  ");

        AuthState admin = login("admin@example.com");
        for (TestProduct product : java.util.List.of(heroProduct, lifestyleProduct, announcementProduct)) {
            mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                            .param("version", "0").cookie(admin.session()), admin.csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_IN_USE"));
            assertThat(count("products", "id", product.id())).isOne();
        }
    }

    @Test
    void homeProductLinksRequireExistingSlugAndHeroStoresCanonicalTrimmedValue() throws Exception {
        TestProduct product = createProduct("HIDDEN", 0);
        AuthState admin = login("admin@example.com");
        UUID slideId = jdbcTemplate.queryForObject(
                "SELECT id FROM home_hero_slides ORDER BY sort_order LIMIT 1", UUID.class);
        long slideVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM home_hero_slides WHERE id = ?", Long.class, slideId);
        int slideSortOrder = jdbcTemplate.queryForObject(
                "SELECT sort_order FROM home_hero_slides WHERE id = ?", Integer.class, slideId);
        String missingSlug = "missing-product-" + UUID.randomUUID();

        mockMvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/hero-slides/{id}", slideId)
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(heroBody(missingSlug, slideSortOrder, slideVersion)), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("상품 링크 대상을 찾을 수 없습니다."));

        mockMvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/hero-slides/{id}", slideId)
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(heroBody("  " + product.slug() + "  ", slideSortOrder, slideVersion)), admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkValue").value(product.slug()));

        long announcementVersion = jdbcTemplate.queryForObject("""
                SELECT version FROM home_sections WHERE section_key = 'ANNOUNCEMENT_HEADER'
                """, Long.class);
        int announcementSortOrder = jdbcTemplate.queryForObject("""
                SELECT sort_order FROM home_sections WHERE section_key = 'ANNOUNCEMENT_HEADER'
                """, Integer.class);
        String announcementBody = "{\"title\":\"공지\",\"content\":\"{\\\"announcementText\\\":\\\"안내\\\","
                + "\\\"linkType\\\":\\\"PRODUCT\\\",\\\"linkValue\\\":\\\"" + missingSlug
                + "\\\"}\",\"sortOrder\":" + announcementSortOrder + ",\"version\":" + announcementVersion + "}";
        mockMvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/home-sections/81000000-0000-0000-0000-000000000001")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(announcementBody), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("상품 링크 대상을 찾을 수 없습니다."));
    }

    @Test
    void customerAndMissingCsrfCannotDeleteProduct() throws Exception {
        TestProduct product = createProduct("HIDDEN", 0);
        AuthState customer = login("demo@example.com");

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0").cookie(customer.session()), customer.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        AuthState admin = login("admin@example.com");
        mockMvc.perform(delete("/api/v1/admin/products/{id}", product.id())
                        .param("version", "0").cookie(admin.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(count("products", "id", product.id())).isOne();
    }

    @Test
    void versionQueryParameterIsRequired() throws Exception {
        TestProduct product = createProduct("HIDDEN", 0);
        AuthState admin = login("admin@example.com");

        mockMvc.perform(withCsrf(delete("/api/v1/admin/products/{id}", product.id())
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(count("products", "id", product.id())).isOne();
    }

    @Test
    void v7MakesOnlyHistoricalCatalogReferencesNullableWithSetNullForeignKeys() {
        assertThat(columnNullable("order_items", "product_id")).isEqualTo("YES");
        assertThat(columnNullable("order_items", "variant_id")).isEqualTo("YES");
        assertThat(columnNullable("inventory_reservations", "variant_id")).isEqualTo("YES");
        assertThat(foreignKeyDeleteRule("order_items_product_id_fkey")).isEqualTo("SET NULL");
        assertThat(foreignKeyDeleteRule("order_items_variant_id_fkey")).isEqualTo("SET NULL");
        assertThat(foreignKeyDeleteRule("inventory_reservations_variant_id_fkey")).isEqualTo("SET NULL");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletionWaitsForCartBeforeVariantSoConcurrentCheckoutCannotDeadlock() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TestProduct product = transactions.execute(status -> createProduct("HIDDEN", 0));
        assertThat(product).isNotNull();
        UUID visitorId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID cartItemId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO visitors (id, token_hash, expires_at)
                    VALUES (?, ?, ?)
                    """, visitorId, UUID.randomUUID().toString(), OffsetDateTime.now().plusDays(1));
            jdbcTemplate.update("INSERT INTO carts (id, visitor_id, status) VALUES (?, ?, 'ACTIVE')",
                    cartId, visitorId);
            jdbcTemplate.update("""
                    INSERT INTO cart_items (id, cart_id, variant_id, quantity, unit_price_at_add)
                    VALUES (?, ?, ?, 1, 19000)
                    """, cartItemId, cartId, product.variantId());
        });

        CountDownLatch cartLocked = new CountDownLatch(1);
        CountDownLatch allowVariantUpdate = new CountDownLatch(1);
        CompletableFuture<Integer> deleteBackendPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> checkout = CompletableFuture.runAsync(() ->
                    transactions.executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject("SELECT id FROM cart_items WHERE id = ? FOR UPDATE",
                                UUID.class, cartItemId);
                        cartLocked.countDown();
                        await(allowVariantUpdate);
                        assertThat(jdbcTemplate.update("""
                                UPDATE product_variants
                                   SET stock_quantity = stock_quantity - 1, version = version + 1
                                 WHERE id = ? AND stock_quantity > 0
                                """, product.variantId())).isOne();
                    }), executor);

            assertThat(cartLocked.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> deletion = CompletableFuture.runAsync(() ->
                    transactions.executeWithoutResult(status -> {
                        deleteBackendPid.complete(jdbcTemplate.queryForObject(
                                "SELECT pg_backend_pid()", Integer.class));
                        adminService.deleteProduct(product.id(), 0L, false, ADMIN_ID);
                    }), executor);

            int backendPid = deleteBackendPid.get(5, TimeUnit.SECONDS);
            assertThat(waitUntilDatabaseSessionBlocks(backendPid, Duration.ofSeconds(5))).isTrue();
            allowVariantUpdate.countDown();
            checkout.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);
            assertThat(count("products", "id", product.id())).isZero();
        } finally {
            allowVariantUpdate.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE resource_id = ?", product.id().toString());
                jdbcTemplate.update("DELETE FROM cart_items WHERE cart_id = ?", cartId);
                jdbcTemplate.update("DELETE FROM carts WHERE id = ?", cartId);
                jdbcTemplate.update("DELETE FROM visitors WHERE id = ?", visitorId);
                jdbcTemplate.update("DELETE FROM wishlist_items WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM collection_products WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM product_images WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM product_categories WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM product_species WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM product_variants WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", product.id());
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void homeProductLinkWaitsForConcurrentDeletionAndRejectsRemovedTarget() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TestProduct product = transactions.execute(status -> createProduct("HIDDEN", 0));
        assertThat(product).isNotNull();
        HomeSlideState slide = jdbcTemplate.queryForObject("""
                SELECT id, sort_order, version
                  FROM home_hero_slides
                 ORDER BY sort_order
                 LIMIT 1
                """, (rs, rowNum) -> new HomeSlideState(
                rs.getObject("id", UUID.class), rs.getInt("sort_order"), rs.getLong("version")));
        assertThat(slide).isNotNull();

        CountDownLatch productLocked = new CountDownLatch(1);
        CountDownLatch allowProductDelete = new CountDownLatch(1);
        CompletableFuture<Integer> homeBackendPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> deletion = CompletableFuture.runAsync(() ->
                    transactions.executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject("SELECT id FROM products WHERE id = ? FOR NO KEY UPDATE",
                                UUID.class, product.id());
                        productLocked.countDown();
                        await(allowProductDelete);
                        jdbcTemplate.update("DELETE FROM product_variants WHERE product_id = ?", product.id());
                        jdbcTemplate.update("DELETE FROM products WHERE id = ?", product.id());
                    }), executor);

            assertThat(productLocked.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> homeUpdate = CompletableFuture.runAsync(() ->
                    transactions.executeWithoutResult(status -> {
                        homeBackendPid.complete(jdbcTemplate.queryForObject(
                                "SELECT pg_backend_pid()", Integer.class));
                        adminService.updateHeroSlide(slide.id(), new HeroSlideUpdate(
                                "동시성 링크", "삭제 경합 검증", "demo/home/summer-hydration.webp",
                                "동시성 검증", "PRODUCT", product.slug(), "HIDDEN", slide.sortOrder(),
                                slide.version()), ADMIN_ID);
                    }), executor);

            int backendPid = homeBackendPid.get(5, TimeUnit.SECONDS);
            assertThat(waitUntilDatabaseSessionBlocks(backendPid, Duration.ofSeconds(5))).isTrue();
            allowProductDelete.countDown();
            deletion.get(10, TimeUnit.SECONDS);
            try {
                homeUpdate.get(10, TimeUnit.SECONDS);
                throw new AssertionError("Deleted product link update should have failed");
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOfSatisfying(ApiException.class, apiException -> {
                    assertThat(apiException.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(apiException.getMessage()).isEqualTo("상품 링크 대상을 찾을 수 없습니다.");
                });
            }
            assertThat(jdbcTemplate.queryForObject("SELECT link_value FROM home_hero_slides WHERE id = ?",
                    String.class, slide.id())).isNotEqualTo(product.slug());
        } finally {
            allowProductDelete.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE resource_id = ?", product.id().toString());
                jdbcTemplate.update("DELETE FROM product_variants WHERE product_id = ?", product.id());
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", product.id());
            });
        }
    }

    private TestProduct createProduct(String status, long version) {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        String slug = "delete-test-" + productId;
        String name = "삭제 테스트 상품 " + productId;
        String sku = "DELETE-" + variantId;
        jdbcTemplate.update("""
                INSERT INTO products (
                    id, brand_id, slug, name, status, attributes, featured, published_at, version
                ) VALUES (?, ?, ?, ?, ?, '{}'::jsonb, false,
                    CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END, ?)
                """, productId, BRAND_ID, slug, name, status, status, version);
        jdbcTemplate.update("""
                INSERT INTO product_variants (
                    id, product_id, sku, name, price, stock_quantity, status, sort_order
                ) VALUES (?, ?, ?, '기본 옵션', 19000, 3, 'ACTIVE', 1)
                """, variantId, productId, sku);
        return new TestProduct(productId, variantId, slug, name, sku);
    }

    private long count(String table, String column, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
    }

    private String columnNullable(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String foreignKeyDeleteRule(String constraintName) {
        return jdbcTemplate.queryForObject("""
                SELECT delete_rule
                  FROM information_schema.referential_constraints
                 WHERE constraint_schema = 'public' AND constraint_name = ?
                """, String.class, constraintName);
    }

    private String heroBody(String linkValue, int sortOrder, long version) {
        return """
                {"title":"상품 링크","description":"상품 링크 검증","imageStorageKey":"demo/home/summer-hydration.webp","imageAlt":"상품 링크 검증","linkType":"PRODUCT","linkValue":"%s","status":"HIDDEN","sortOrder":%d,"version":%d}
                """.formatted(linkValue, sortOrder, version);
    }

    private boolean waitUntilDatabaseSessionBlocks(int backendPid, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String waitEventType = jdbcTemplate.queryForObject(
                    "SELECT wait_event_type FROM pg_stat_activity WHERE pid = ?", String.class, backendPid);
            if ("Lock".equals(waitEventType)) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent delete");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating concurrent delete", exception);
        }
    }

    private AuthState login(String email) throws Exception {
        CsrfState anonymousCsrf = csrf();
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"DemoPassword123!\"}"), anonymousCsrf))
                .andExpect(status().isOk()).andReturn();
        Cookie session = requireResponseCookie(login, "SESSION");
        return new AuthState(session, csrf(session));
    }

    private CsrfState csrf(Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (cookies.length > 0) request.cookie(cookies);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return new CsrfState(requireResponseCookie(result, "XSRF-TOKEN"),
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.token"));
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

    private record TestProduct(UUID id, UUID variantId, String slug, String name, String sku) {
    }

    private record HomeSlideState(UUID id, int sortOrder, long version) {
    }

    private record CsrfState(Cookie cookie, String token) {
    }

    private record AuthState(Cookie session, CsrfState csrf) {
    }
}
