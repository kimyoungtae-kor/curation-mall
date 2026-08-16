package kr.co.petcuration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class MerchandisingApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsSevenSectionHomeDataToAnonymousVisitorsInDisplayOrder() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.announcement.text").value("5만원 이상 무료배송"))
                .andExpect(jsonPath("$.data.announcement.link.type").value("HELP"))
                .andExpect(jsonPath("$.data.heroSlides", hasSize(3)))
                .andExpect(jsonPath("$.data.heroSlides[*].sortOrder", contains(1, 2, 3)))
                .andExpect(jsonPath("$.data.heroSlides[0].image.url")
                        .value("/media/demo/home/summer-hydration.webp"))
                .andExpect(jsonPath("$.data.featuredCollections[*].slug", contains(
                        "summer-hydration",
                        "safe-road-trip",
                        "calm-pet-room"
                )))
                .andExpect(jsonPath("$.data.popularProducts", hasSize(8)))
                .andExpect(jsonPath("$.data.newProducts[*].slug", contains(
                        "wool-play-tunnel",
                        "soft-toy-basket",
                        "color-pop-litter-tray",
                        "clean-scoop-set",
                        "tofu-litter-box",
                        "mini-treat-pouch",
                        "picnic-rope-leash",
                        "cloud-pocket-raincoat"
                )))
                .andExpect(jsonPath("$.data.explore.species[*].slug", contains("dog", "cat")))
                .andExpect(jsonPath("$.data.explore.categories", hasSize(8)))
                .andExpect(jsonPath("$.data.explore.brands", hasSize(4)))
                .andExpect(jsonPath("$.data.lifestyleContents", hasSize(1)))
                .andExpect(jsonPath("$.data.lifestyleContents[0].link.value").value("calm-pet-room"))
                .andExpect(jsonPath("$.data.serviceGuide.shippingFee").value(3000))
                .andExpect(jsonPath("$.data.serviceGuide.freeShippingThreshold").value(50000))
                .andExpect(jsonPath("$.data.serviceGuide.links", contains(
                        "shipping-returns",
                        "terms",
                        "privacy"
                )));
    }

    @Test
    void listsPublicCollectionsByConfiguredOrderWithPageMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/collections").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains("summer-hydration", "safe-road-trip")))
                .andExpect(jsonPath("$.data[*].sortOrder", contains(10, 20)))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.first").value(true))
                .andExpect(jsonPath("$.page.last").value(false));

        mockMvc.perform(get("/api/v1/collections").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains("calm-pet-room")))
                .andExpect(jsonPath("$.page.last").value(true));
    }

    @Test
    void readsCollectionDetailAndPreservesConnectedProductOrder() throws Exception {
        mockMvc.perform(get("/api/v1/collections/summer-hydration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("summer-hydration"))
                .andExpect(jsonPath("$.data.heroImage.url")
                        .value("/media/demo/home/summer-hydration.webp"))
                .andExpect(jsonPath("$.data.products[*].slug", contains(
                        "oasis-ceramic-water-bowl",
                        "cozy-corner-pet-bed"
                )))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
    }

    @Test
    void hidesHiddenFutureAndExpiredCollectionsFromListDetailAndHome() throws Exception {
        jdbcTemplate.update("""
                UPDATE collections
                   SET status = 'HIDDEN'
                 WHERE slug = 'summer-hydration'
                """);
        jdbcTemplate.update("""
                UPDATE collections
                   SET status = 'PUBLISHED',
                       published_at = CURRENT_TIMESTAMP + INTERVAL '1 day'
                 WHERE slug = 'safe-road-trip'
                """);
        jdbcTemplate.update("""
                UPDATE collections
                   SET status = 'PUBLISHED',
                       published_at = CURRENT_TIMESTAMP - INTERVAL '2 days',
                       expires_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                 WHERE slug = 'calm-pet-room'
                """);

        mockMvc.perform(get("/api/v1/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        for (String slug : new String[]{"summer-hydration", "safe-road-trip", "calm-pet-room"}) {
            mockMvc.perform(get("/api/v1/collections/" + slug))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featuredCollections", hasSize(0)));
    }

    @Test
    void requiresPublicationTimeAndOnlyFeaturesSelectedCollectionsOnHome() throws Exception {
        jdbcTemplate.update("""
                UPDATE collections
                   SET featured = FALSE
                 WHERE slug = 'safe-road-trip'
                """);
        jdbcTemplate.update("""
                UPDATE collections
                   SET published_at = NULL
                 WHERE slug = 'calm-pet-room'
                """);

        mockMvc.perform(get("/api/v1/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains("summer-hydration", "safe-road-trip")));

        mockMvc.perform(get("/api/v1/collections/calm-pet-room"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featuredCollections[*].slug", contains("summer-hydration")));
    }

    @Test
    void excludesNonPublicConnectedProductsFromCollectionAndHomeProductSections() throws Exception {
        jdbcTemplate.update("""
                UPDATE products
                   SET status = 'HIDDEN'
                 WHERE slug = 'oasis-ceramic-water-bowl'
                """);
        jdbcTemplate.update("""
                UPDATE products
                   SET status = 'PUBLISHED',
                       published_at = CURRENT_TIMESTAMP + INTERVAL '1 day'
                 WHERE slug = 'cozy-corner-pet-bed'
                """);

        mockMvc.perform(get("/api/v1/collections/summer-hydration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products", hasSize(0)));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.popularProducts[*].slug", not(hasItem("oasis-ceramic-water-bowl"))))
                .andExpect(jsonPath("$.data.newProducts[*].slug", not(hasItem("cozy-corner-pet-bed"))));
    }

    @Test
    void appliesPublicationStatusWindowAndOrderToHeroAndLifestyleContent() throws Exception {
        jdbcTemplate.update("""
                UPDATE home_hero_slides
                   SET status = CASE sort_order
                           WHEN 2 THEN 'HIDDEN'
                           ELSE 'PUBLISHED'
                       END,
                       published_at = CASE sort_order
                           WHEN 3 THEN CURRENT_TIMESTAMP + INTERVAL '1 day'
                           ELSE CURRENT_TIMESTAMP - INTERVAL '2 days'
                       END,
                       expires_at = NULL
                """);
        jdbcTemplate.update("""
                UPDATE home_lifestyle_contents
                   SET status = 'HIDDEN',
                       published_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                """);

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heroSlides[*].sortOrder", contains(1)))
                .andExpect(jsonPath("$.data.lifestyleContents", hasSize(0)));
    }

    @Test
    void returnsStableProblemDetailsForMissingAndMalformedCollectionRequests() throws Exception {
        mockMvc.perform(get("/api/v1/collections/not-a-collection"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/collections/not-a-collection"));

        assertValidationProblem("page", "-1");
        assertValidationProblem("size", "101");
        assertValidationProblem("sort", "publishedAt,desc");

        mockMvc.perform(get("/api/v1/collections/INVALID_SLUG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));
    }

    private void assertValidationProblem(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/collections").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(parameter));
    }
}
