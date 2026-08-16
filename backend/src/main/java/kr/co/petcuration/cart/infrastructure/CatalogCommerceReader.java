package kr.co.petcuration.cart.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogCommerceReader {

    private static final String VARIANT_SELECT = """
            SELECT pv.id AS variant_id,
                   p.id AS product_id,
                   p.slug,
                   b.name AS brand_name,
                   p.name AS product_name,
                   pv.sku,
                   pv.name AS option_label,
                   pv.price,
                   pv.stock_quantity,
                   (p.status = 'PUBLISHED' AND p.published_at IS NOT NULL
                       AND p.published_at <= CURRENT_TIMESTAMP) AS product_public,
                   (pv.status = 'ACTIVE') AS variant_active,
                   (SELECT CASE
                               WHEN pi.storage_key LIKE 'http://%' OR pi.storage_key LIKE 'https://%'
                                   THEN pi.storage_key
                               ELSE '/media/' || pi.storage_key
                           END
                      FROM product_images pi
                     WHERE pi.product_id = p.id
                     ORDER BY pi.sort_order, pi.id
                     LIMIT 1) AS thumbnail_url
              FROM product_variants pv
              JOIN products p ON p.id = pv.product_id
              JOIN brands b ON b.id = p.brand_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CatalogCommerceReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<VariantData> findVariant(UUID variantId) {
        List<VariantData> rows = jdbcTemplate.query(
                VARIANT_SELECT + " WHERE pv.id = :variantId",
                Map.of("variantId", variantId),
                this::mapVariant
        );
        return rows.stream().findFirst();
    }

    public Map<UUID, VariantData> findVariants(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        List<VariantData> rows = jdbcTemplate.query(
                VARIANT_SELECT + " WHERE pv.id IN (:variantIds)",
                new MapSqlParameterSource("variantIds", variantIds),
                this::mapVariant
        );
        Map<UUID, VariantData> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.variantId(), row));
        return result;
    }

    public Optional<ProductData> findProduct(UUID productId) {
        List<ProductData> rows = findProducts(List.of(productId));
        return rows.stream().findFirst();
    }

    public List<ProductData> findProducts(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT p.id AS product_id,
                       p.slug,
                       b.name AS brand_name,
                       p.name,
                       p.short_description AS summary,
                       (p.status = 'PUBLISHED' AND p.published_at IS NOT NULL
                           AND p.published_at <= CURRENT_TIMESTAMP) AS product_public,
                       (SELECT MIN(pv.price)
                          FROM product_variants pv
                         WHERE pv.product_id = p.id AND pv.status = 'ACTIVE') AS minimum_price,
                       EXISTS (SELECT 1 FROM product_variants pv
                                WHERE pv.product_id = p.id AND pv.status = 'ACTIVE'
                                  AND pv.stock_quantity > 0) AS in_stock,
                       (SELECT CASE
                                   WHEN pi.storage_key LIKE 'http://%' OR pi.storage_key LIKE 'https://%'
                                       THEN pi.storage_key
                                   ELSE '/media/' || pi.storage_key
                               END
                          FROM product_images pi
                         WHERE pi.product_id = p.id
                         ORDER BY pi.sort_order, pi.id
                         LIMIT 1) AS thumbnail_url
                  FROM products p
                  JOIN brands b ON b.id = p.brand_id
                 WHERE p.id IN (:productIds)
                """,
                new MapSqlParameterSource("productIds", productIds),
                (resultSet, rowNumber) -> new ProductData(
                        resultSet.getObject("product_id", UUID.class),
                        resultSet.getString("slug"),
                        resultSet.getString("brand_name"),
                        resultSet.getString("name"),
                        resultSet.getString("summary"),
                        resultSet.getBoolean("product_public"),
                        nullableLong(resultSet, "minimum_price"),
                        resultSet.getBoolean("in_stock"),
                        resultSet.getString("thumbnail_url")
                )
        );
    }

    private VariantData mapVariant(ResultSet resultSet, int rowNumber) throws SQLException {
        return new VariantData(
                resultSet.getObject("variant_id", UUID.class),
                resultSet.getObject("product_id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("brand_name"),
                resultSet.getString("product_name"),
                resultSet.getString("sku"),
                resultSet.getString("option_label"),
                resultSet.getLong("price"),
                resultSet.getInt("stock_quantity"),
                resultSet.getBoolean("product_public"),
                resultSet.getBoolean("variant_active"),
                resultSet.getString("thumbnail_url")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    public record VariantData(
            UUID variantId,
            UUID productId,
            String slug,
            String brandName,
            String productName,
            String sku,
            String optionLabel,
            long price,
            int stockQuantity,
            boolean productPublic,
            boolean variantActive,
            String thumbnailUrl
    ) {
        public boolean purchasable() {
            return productPublic && variantActive && stockQuantity > 0;
        }
    }

    public record ProductData(
            UUID productId,
            String slug,
            String brandName,
            String name,
            String summary,
            boolean productPublic,
            Long minimumPrice,
            boolean inStock,
            String thumbnailUrl
    ) {
    }
}
