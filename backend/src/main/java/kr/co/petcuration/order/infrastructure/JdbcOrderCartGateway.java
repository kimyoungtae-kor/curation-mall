package kr.co.petcuration.order.infrastructure;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.co.petcuration.order.application.CommerceActor;
import kr.co.petcuration.order.application.OrderCartGateway;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderCartGateway implements OrderCartGateway {

    private static final String SELECT_LINES = """
            SELECT ci.id AS cart_item_id,
                   p.id AS product_id,
                   pv.id AS variant_id,
                   p.name AS product_name,
                   b.name AS brand_name,
                   pv.sku,
                   pv.name AS option_label,
                   (SELECT '/media/' || pi.storage_key
                      FROM product_images pi
                     WHERE pi.product_id = p.id
                     ORDER BY pi.sort_order
                     LIMIT 1) AS image_url,
                   pv.price AS unit_price,
                   ci.quantity,
                   pv.stock_quantity,
                   (p.status = 'PUBLISHED'
                     AND p.published_at IS NOT NULL
                     AND p.published_at <= CURRENT_TIMESTAMP
                     AND pv.status = 'ACTIVE') AS available
              FROM cart_items ci
              JOIN carts c ON c.id = ci.cart_id AND c.status = 'ACTIVE'
              JOIN product_variants pv ON pv.id = ci.variant_id
              JOIN products p ON p.id = pv.product_id
              JOIN brands b ON b.id = p.brand_id
             WHERE ci.id IN (:ids)
               AND %s
             %s
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public JdbcOrderCartGateway(NamedParameterJdbcTemplate jdbcTemplate, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Override
    public List<CartLine> load(CommerceActor actor, List<UUID> cartItemIds) {
        return load(actor, cartItemIds, false);
    }

    @Override
    public List<CartLine> loadForOrderCreation(CommerceActor actor, List<UUID> cartItemIds) {
        return load(actor, cartItemIds, true);
    }

    private List<CartLine> load(CommerceActor actor, List<UUID> cartItemIds, boolean lockCartItems) {
        entityManager.flush();
        Map<String, Object> parameters = parameters(actor, cartItemIds);
        String sql = SELECT_LINES.formatted(
                actor.isMember() ? "c.user_id = :ownerId" : "c.visitor_id = :ownerId",
                lockCartItems ? "ORDER BY ci.id FOR UPDATE OF ci" : ""
        );
        List<CartLine> queried = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> new CartLine(
                rs.getObject("cart_item_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getObject("variant_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("brand_name"),
                rs.getString("sku"),
                rs.getString("option_label"),
                rs.getString("image_url"),
                rs.getLong("unit_price"),
                rs.getInt("quantity"),
                rs.getInt("stock_quantity"),
                rs.getBoolean("available")
        ));

        Map<UUID, CartLine> byId = new LinkedHashMap<>();
        queried.forEach(line -> byId.put(line.cartItemId(), line));
        return cartItemIds.stream().distinct().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public int remove(CommerceActor actor, List<UUID> cartItemIds) {
        entityManager.flush();
        return jdbcTemplate.update("""
                DELETE FROM cart_items ci
                 USING carts c
                 WHERE ci.cart_id = c.id
                   AND ci.id IN (:ids)
                   AND c.status = 'ACTIVE'
                   AND %s
                """.formatted(actor.isMember() ? "c.user_id = :ownerId" : "c.visitor_id = :ownerId"),
                parameters(actor, cartItemIds));
    }

    private Map<String, Object> parameters(CommerceActor actor, List<UUID> ids) {
        Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("ids", ids);
        parameters.put("ownerId", actor.isMember() ? actor.userId() : actor.visitorId());
        return parameters;
    }
}
