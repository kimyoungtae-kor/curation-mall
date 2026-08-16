package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminApiIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void customerCannotReadAdminApi() throws Exception {
        AuthState customer = login("demo@example.com");
        mockMvc.perform(get("/api/v1/admin/products").cookie(customer.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminListsProductsCreatesDraftAndUpdatesStockWithVersion() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(get("/api/v1/admin/products").cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(24));

        String productBody = """
                {
                  "brandId":"20000000-0000-0000-0000-000000000001",
                  "slug":"admin-created-demo-product",
                  "name":"관리자 등록 데모 상품",
                  "summary":"관리자 통합 테스트 상품",
                  "description":"시연용 상품 등록 흐름을 검증합니다.",
                  "status":"DRAFT",
                  "featured":false,
                  "categoryIds":["40000000-0000-0000-0000-000000000005"],
                  "speciesIds":["30000000-0000-0000-0000-000000000001"],
                  "variants":[{
                    "sku":"ADMIN-DEMO-SKU","optionLabel":"기본 옵션","price":19000,
                    "stockQuantity":5,"status":"ACTIVE","sortOrder":10
                  }],
                  "images":[]
                }
                """;
        mockMvc.perform(withCsrf(post("/api/v1/admin/products")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON).content(productBody), admin.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        Long version = jdbcTemplate.queryForObject("""
                SELECT version FROM product_variants WHERE id = '60000000-0000-0000-0000-000000000001'
                """, Long.class);
        mockMvc.perform(withCsrf(patch("/api/v1/admin/variants/60000000-0000-0000-0000-000000000001/stock")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockQuantity\":33,\"version\":" + version + "}"), admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(33));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM admin_audit_logs WHERE admin_user_id = '11000000-0000-0000-0000-000000000002'
                """, Long.class)).isEqualTo(2L);
    }

    @Test
    void staleVariantVersionMakesFullProductSaveConflictWithoutOverwritingCurrentStock() throws Exception {
        AuthState admin = login("admin@example.com");
        MvcResult productResult = mockMvc.perform(
                        get("/api/v1/admin/products/50000000-0000-0000-0000-000000000001")
                                .cookie(admin.session()))
                .andExpect(status().isOk())
                .andReturn();
        ObjectNode updateBody = (ObjectNode) objectMapper.readTree(
                productResult.getResponse().getContentAsString()).get("data").deepCopy();
        updateBody.remove("id");
        long staleVariantVersion = updateBody.get("variants").get(0).get("version").longValue();

        mockMvc.perform(withCsrf(patch("/api/v1/admin/variants/60000000-0000-0000-0000-000000000001/stock")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockQuantity\":33,\"version\":" + staleVariantVersion + "}"), admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(staleVariantVersion + 1));

        mockMvc.perform(withCsrf(put("/api/v1/admin/products/50000000-0000-0000-0000-000000000001")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT stock_quantity FROM product_variants
                 WHERE id = '60000000-0000-0000-0000-000000000001'
                """, Integer.class)).isEqualTo(33);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT version FROM product_variants
                 WHERE id = '60000000-0000-0000-0000-000000000001'
                """, Long.class)).isEqualTo(staleVariantVersion + 1);
    }

    @Test
    void foreignVariantIdInFullProductSaveConflictsAndDoesNotInsertOrMoveVariant() throws Exception {
        AuthState admin = login("admin@example.com");
        MvcResult productResult = mockMvc.perform(
                        get("/api/v1/admin/products/50000000-0000-0000-0000-000000000001")
                                .cookie(admin.session()))
                .andExpect(status().isOk())
                .andReturn();
        ObjectNode updateBody = (ObjectNode) objectMapper.readTree(
                productResult.getResponse().getContentAsString()).get("data").deepCopy();
        updateBody.remove("id");
        ObjectNode firstVariant = (ObjectNode) updateBody.get("variants").get(0);
        firstVariant.put("id", "60000000-0000-0000-0000-000000000003");
        firstVariant.put("version", jdbcTemplate.queryForObject("""
                SELECT version FROM product_variants
                 WHERE id = '60000000-0000-0000-0000-000000000003'
                """, Long.class));

        mockMvc.perform(withCsrf(put("/api/v1/admin/products/50000000-0000-0000-0000-000000000001")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT product_id FROM product_variants
                 WHERE id = '60000000-0000-0000-0000-000000000003'
                """, String.class)).isEqualTo("50000000-0000-0000-0000-000000000002");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM product_variants
                 WHERE product_id = '50000000-0000-0000-0000-000000000001'
                   AND id = '60000000-0000-0000-0000-000000000003'
                """, Long.class)).isZero();
    }

    @Test
    void adminTransitionsPaidOrderOnlyAlongAllowedPath() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(get("/api/v1/admin/orders").cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(withCsrf(post("/api/v1/admin/orders/P20260812-DEMO0001/transitions")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"PREPARING\",\"reason\":\"상품 준비 시작\",\"version\":0}"), admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.orderStatus").value("PREPARING"));

        mockMvc.perform(withCsrf(post("/api/v1/admin/orders/P20260812-DEMO0001/transitions")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"DELIVERED\",\"reason\":\"허용되지 않은 건너뛰기\",\"version\":1}"), admin.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void adminUpdatesAnnouncementAndHeroWithOptimisticVersion() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(put("/api/v1/admin/home-sections/81000000-0000-0000-0000-000000000001")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"공지와 탐색\",\"content\":\"{\\\"announcementText\\\":\\\"시연 배송 안내\\\",\\\"linkType\\\":\\\"HELP\\\",\\\"linkValue\\\":\\\"shipping-returns\\\"}\",\"sortOrder\":1,\"version\":0}"), admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(withCsrf(put("/api/v1/admin/hero-slides/82000000-0000-0000-0000-000000000001")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"시원한 한 모금","description":"여름 큐레이션","imageStorageKey":"demo/home/summer-hydration.webp","imageAlt":"반려동물 음수 공간","linkType":"COLLECTION","linkValue":"summer-hydration","status":"PUBLISHED","sortOrder":1,"version":0}
                                """), admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("시원한 한 모금"));
    }

    @Test
    void invalidAnnouncementSchemaIsRejectedAndPublicHomeRemainsReadable() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(put("/api/v1/admin/home-sections/81000000-0000-0000-0000-000000000001")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"공지\",\"content\":\"{\\\"announcementText\\\":\\\"안내\\\",\\\"linkType\\\":\\\"UNKNOWN\\\",\\\"linkValue\\\":\\\"terms\\\"}\",\"sortOrder\":1,\"version\":0}"), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.announcement.text").value("5만원 이상 무료배송"))
                .andExpect(jsonPath("$.data.announcement.link.type").value("HELP"));
    }

    @Test
    void missingServiceGuideFieldIsRejectedAndPublicHomeRemainsReadable() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(put("/api/v1/admin/home-sections/81000000-0000-0000-0000-000000000007")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"서비스 안내\",\"content\":\"{\\\"shippingFee\\\":3000,\\\"links\\\":[\\\"terms\\\"]}\",\"sortOrder\":7,\"version\":0}"), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceGuide.shippingFee").value(3000))
                .andExpect(jsonPath("$.data.serviceGuide.freeShippingThreshold").value(50000));
    }

    @Test
    void nonStringServiceGuideLinkIsRejectedAndPublicHomeRemainsReadable() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(put("/api/v1/admin/home-sections/81000000-0000-0000-0000-000000000007")
                        .cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"서비스 안내\",\"content\":\"{\\\"shippingFee\\\":3000,\\\"freeShippingThreshold\\\":50000,\\\"links\\\":[\\\"terms\\\",7]}\",\"sortOrder\":7,\"version\":0}"), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceGuide.links.length()").value(3));
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

    private record CsrfState(Cookie cookie, String token) {
    }

    private record AuthState(Cookie session, CsrfState csrf) {
    }
}
