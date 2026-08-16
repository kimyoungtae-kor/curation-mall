package kr.co.petcuration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").isNotEmpty());
    }

    @Test
    void listsActiveProductsFromLocalTestSeed() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(24))
                .andExpect(jsonPath("$.data[0].slug").value("wool-play-tunnel"))
                .andExpect(jsonPath("$.data[0].currency").value("KRW"));
    }

    @Test
    void searchesByProductAndBrandNameAndEscapesLikeWildcards() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("q", "클라우드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("cloud-fit-car-seat")))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(get("/api/v1/catalog/products").param("q", "PAWFORM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("cloud-fit-car-seat")));

        mockMvc.perform(get("/api/v1/catalog/products").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void filtersByBrandCategorySpeciesAndCombinesFiltersWithAnd() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("brand", "nook-and-tail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("cozy-corner-pet-bed")))
                .andExpect(jsonPath("$.data[*].slug", hasItem("module-cat-step-tower")))
                .andExpect(jsonPath("$.page.totalElements").value(9));

        mockMvc.perform(get("/api/v1/catalog/products").param("category", "feeding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("oasis-ceramic-water-bowl")));

        mockMvc.perform(get("/api/v1/catalog/products").param("species", "cat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("cozy-corner-pet-bed")))
                .andExpect(jsonPath("$.data[*].slug", hasItem("module-cat-step-tower")))
                .andExpect(jsonPath("$.data[*].slug", hasItem("oasis-ceramic-water-bowl")))
                .andExpect(jsonPath("$.page.totalElements").value(18));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("brand", "nook-and-tail")
                        .param("species", "dog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("cozy-corner-pet-bed")));
    }

    @Test
    void returnsAnEmptyPageForUnknownValidFilter() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("category", "unknown-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    void inStockTrueRequiresAnActiveVariantWithPositiveStock() throws Exception {
        jdbcTemplate.update("""
                UPDATE product_variants
                   SET stock_quantity = 0
                 WHERE product_id = '50000000-0000-0000-0000-000000000004'
                """);
        jdbcTemplate.update("""
                UPDATE product_variants
                   SET status = 'INACTIVE'
                 WHERE product_id = '50000000-0000-0000-0000-000000000001'
                """);

        mockMvc.perform(get("/api/v1/catalog/products").param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(21))
                .andExpect(jsonPath("$.data[*].slug", not(hasItem("cozy-corner-pet-bed"))))
                .andExpect(jsonPath("$.data[*].slug", not(hasItem("oasis-ceramic-water-bowl"))));

        mockMvc.perform(get("/api/v1/catalog/products").param("inStock", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(24));
    }

    @Test
    void onlyListsPublishedProductsAndNeverReadsAnUnpublishedProduct() throws Exception {
        jdbcTemplate.update("""
                UPDATE products
                   SET status = CASE slug
                       WHEN 'cloud-fit-car-seat' THEN 'DRAFT'
                       WHEN 'oasis-ceramic-water-bowl' THEN 'HIDDEN'
                       WHEN 'module-cat-step-tower' THEN 'DISCONTINUED'
                       ELSE status
                   END
                 WHERE slug IN (
                       'cloud-fit-car-seat',
                       'oasis-ceramic-water-bowl',
                       'module-cat-step-tower'
                 )
                """);

        mockMvc.perform(get("/api/v1/catalog/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(21))
                .andExpect(jsonPath("$.data[*].slug", hasItem("cozy-corner-pet-bed")));

        mockMvc.perform(get("/api/v1/catalog/products/cloud-fit-car-seat"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void hidesPublishedProductsUntilTheirPublicationTime() throws Exception {
        jdbcTemplate.update("""
                UPDATE products
                   SET status = 'PUBLISHED',
                       published_at = CURRENT_TIMESTAMP + INTERVAL '1 day'
                 WHERE slug = 'cloud-fit-car-seat'
                """);

        mockMvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(23))
                .andExpect(jsonPath("$.data[*].slug", not(hasItem("cloud-fit-car-seat"))));

        mockMvc.perform(get("/api/v1/catalog/products/cloud-fit-car-seat"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void placesProductsWithoutActiveVariantPriceLastForBothPriceSortDirections() throws Exception {
        jdbcTemplate.update("""
                UPDATE product_variants
                   SET status = 'INACTIVE'
                 WHERE product_id = '50000000-0000-0000-0000-000000000001'
                """);

        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "price,asc").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[-1].slug").value("oasis-ceramic-water-bowl"));

        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "price,desc").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[-1].slug").value("oasis-ceramic-water-bowl"));
    }

    @Test
    void supportsAllDocumentedSortOrdersUsingMinimumActiveVariantPrice() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "newest,desc").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains(
                        "wool-play-tunnel",
                        "soft-toy-basket",
                        "color-pop-litter-tray",
                        "clean-scoop-set"
                )));

        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "price,asc").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains(
                        "clean-scoop-set",
                        "safe-ride-seat-belt",
                        "mini-treat-pouch"
                )));

        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "price,desc").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains(
                        "module-cat-step-tower",
                        "urban-window-carrier",
                        "tofu-litter-box"
                )));

        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "name,asc").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void preservesPageMetadataAfterFilteringAndSorting() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("species", "cat")
                        .param("sort", "price,asc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(18))
                .andExpect(jsonPath("$.page.totalPages").value(9))
                .andExpect(jsonPath("$.page.first").value(false))
                .andExpect(jsonPath("$.page.last").value(false));
    }

    @Test
    void readsProductDetailBySlug() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/cozy-corner-pet-bed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("포근 코너 펫 베드"))
                .andExpect(jsonPath("$.data.variants", hasSize(2)))
                .andExpect(jsonPath("$.data.variants[0].salePrice").value(69000))
                .andExpect(jsonPath("$.data.variants[1].purchasable").value(false))
                .andExpect(jsonPath("$.data.variants[1].maxPurchaseQuantity").value(0))
                .andExpect(jsonPath("$.data.species", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].url")
                        .value("/media/demo/catalog/cozy-corner-pet-bed.webp"));
    }

    @Test
    void returnsStableErrorShapeForMissingProduct() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/not-a-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/catalog/products/not-a-product"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void validatesListLimit() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    @Test
    void rejectsUnsupportedSortWithValidationProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "featured,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    void rejectsMalformedListQueriesWithValidationProblemDetails() throws Exception {
        assertValidationProblem("page", "not-a-number");
        assertValidationProblem("size", "101");
        assertValidationProblem("inStock", "yes");
        assertValidationProblem("brand", "Nook & Tail");
    }

    private void assertValidationProblem(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(parameter));
    }
}
