package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesIdentitySessionCatalogAndMerchandisingSchemas() {
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("spring_session")).isZero();
        assertThat(count("products")).isEqualTo(24);
        assertThat(count("product_variants")).isEqualTo(26);
        assertThat(count("collections")).isEqualTo(3);
        assertThat(count("collection_products")).isEqualTo(5);
        assertThat(count("home_sections")).isEqualTo(7);
        assertThat(count("home_hero_slides")).isEqualTo(3);
        assertThat(count("home_lifestyle_contents")).isEqualTo(1);
        assertThat(count("carts")).isZero();
        assertThat(count("wishlist_items")).isZero();
        assertThat(count("orders")).isEqualTo(2);
        assertThat(count("payments")).isEqualTo(2);
        assertThat(count("inventory_reservations")).isEqualTo(2);
        assertThat(indexExists("uq_order_items_cart_item")).isTrue();
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? 0 : result;
    }

    private boolean indexExists(String indexName) {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL",
                Boolean.class,
                indexName
        );
        return Boolean.TRUE.equals(result);
    }
}
