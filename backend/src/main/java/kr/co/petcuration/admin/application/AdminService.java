package kr.co.petcuration.admin.application;

import java.sql.Timestamp;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import kr.co.petcuration.admin.api.AdminApiModels;
import kr.co.petcuration.admin.api.AdminApiModels.AdminOrderDetail;
import kr.co.petcuration.admin.api.AdminApiModels.AdminOrderSummary;
import kr.co.petcuration.admin.api.AdminApiModels.HomeSection;
import kr.co.petcuration.admin.api.AdminApiModels.HomeSectionUpdate;
import kr.co.petcuration.admin.api.AdminApiModels.HeroSlide;
import kr.co.petcuration.admin.api.AdminApiModels.HeroSlideUpdate;
import kr.co.petcuration.admin.api.AdminApiModels.Image;
import kr.co.petcuration.admin.api.AdminApiModels.PageResponse;
import kr.co.petcuration.admin.api.AdminApiModels.ProductDetail;
import kr.co.petcuration.admin.api.AdminApiModels.ProductSummary;
import kr.co.petcuration.admin.api.AdminApiModels.ProductUpsertRequest;
import kr.co.petcuration.admin.api.AdminApiModels.ReferenceItem;
import kr.co.petcuration.admin.api.AdminApiModels.StockRequest;
import kr.co.petcuration.admin.api.AdminApiModels.TransitionRequest;
import kr.co.petcuration.admin.api.AdminApiModels.UserSummary;
import kr.co.petcuration.admin.api.AdminApiModels.Variant;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.common.storage.StorageService;
import kr.co.petcuration.merchandising.domain.HomeSectionKey;
import kr.co.petcuration.merchandising.domain.MerchandisingLinkType;
import kr.co.petcuration.order.api.OrderApiModels.PageMetadata;
import kr.co.petcuration.order.application.OrderService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminService {

    private static final Set<String> PRODUCT_STATUSES = Set.of("DRAFT", "PUBLISHED", "HIDDEN", "DISCONTINUED");
    private static final Set<String> VARIANT_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Pattern SAFE_STORAGE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");

    private final JdbcTemplate jdbcTemplate;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    public AdminService(
            JdbcTemplate jdbcTemplate,
            OrderService orderService,
            ObjectMapper objectMapper,
            StorageService storageService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> products(String q, String status, int page, int size) {
        String search = q == null ? "" : q.trim();
        String statusFilter = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String where = "WHERE (? = '' OR p.name ILIKE '%' || ? || '%' OR p.slug ILIKE '%' || ? || '%') "
                + "AND (? = '' OR p.status = ?)";
        Object[] params = {search, search, search, statusFilter, statusFilter};
        long total = jdbcTemplate.queryForObject("SELECT count(*) FROM products p " + where, Long.class, params);
        List<ProductSummary> data = jdbcTemplate.query("""
                SELECT p.id, p.slug, p.name, b.name AS brand_name, p.status,
                       coalesce(min(pv.price) FILTER (WHERE pv.status = 'ACTIVE'), 0) AS minimum_price,
                       coalesce(sum(pv.stock_quantity) FILTER (WHERE pv.status = 'ACTIVE'), 0) AS total_stock,
                       p.version, p.updated_at
                  FROM products p JOIN brands b ON b.id = p.brand_id
                  LEFT JOIN product_variants pv ON pv.product_id = p.id
                """ + where + " GROUP BY p.id, b.name ORDER BY p.updated_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ProductSummary(rs.getObject("id", UUID.class), rs.getString("slug"),
                        rs.getString("name"), rs.getString("brand_name"), rs.getString("status"),
                        rs.getLong("minimum_price"), rs.getInt("total_stock"), rs.getLong("version"),
                        rs.getTimestamp("updated_at").toInstant()),
                search, search, search, statusFilter, statusFilter, size, Math.multiplyExact(page, size));
        return new PageResponse<>(data, page(page, size, total));
    }

    @Transactional(readOnly = true)
    public ProductDetail product(UUID productId) {
        List<ProductDetailBase> bases = jdbcTemplate.query("""
                SELECT id, brand_id, slug, name, short_description, description, status, featured, version
                  FROM products WHERE id = ?
                """, (rs, rowNum) -> new ProductDetailBase(rs.getObject("id", UUID.class),
                rs.getObject("brand_id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getString("short_description"), rs.getString("description"), rs.getString("status"),
                rs.getBoolean("featured"), rs.getLong("version")), productId);
        if (bases.isEmpty()) {
            throw notFound("상품");
        }
        ProductDetailBase base = bases.getFirst();
        List<UUID> categories = jdbcTemplate.query("SELECT category_id FROM product_categories WHERE product_id = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class), productId);
        List<UUID> species = jdbcTemplate.query("SELECT species_id FROM product_species WHERE product_id = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class), productId);
        List<Variant> variants = jdbcTemplate.query("""
                SELECT id, sku, name, price, stock_quantity, status, sort_order, version
                  FROM product_variants WHERE product_id = ? ORDER BY sort_order, id
                """, (rs, rowNum) -> new Variant(rs.getObject("id", UUID.class), rs.getString("sku"),
                rs.getString("name"), rs.getLong("price"), rs.getInt("stock_quantity"), rs.getString("status"),
                rs.getInt("sort_order"), rs.getLong("version")), productId);
        List<Image> images = jdbcTemplate.query("""
                SELECT id, storage_key, alt_text, sort_order FROM product_images
                 WHERE product_id = ? ORDER BY sort_order, id
                """, (rs, rowNum) -> new Image(rs.getObject("id", UUID.class), rs.getString("storage_key"),
                rs.getString("alt_text"), rs.getInt("sort_order")), productId);
        return new ProductDetail(base.id(), base.brandId(), base.slug(), base.name(), base.summary(),
                base.description(), base.status(), base.featured(), categories, species, variants, images, base.version());
    }

    @Transactional
    public ProductDetail createProduct(ProductUpsertRequest request, UUID adminId) {
        validateProductRequest(request);
        UUID productId = UUID.randomUUID();
        Instant publishedAt = request.status().equalsIgnoreCase("PUBLISHED") ? Instant.now() : null;
        jdbcTemplate.update("""
                INSERT INTO products (id, brand_id, slug, name, short_description, description, status,
                                      attributes, featured, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?)
                """, productId, request.brandId(), request.slug(), request.name(), request.summary(),
                request.description(), request.status().toUpperCase(Locale.ROOT), request.featured(),
                publishedAt == null ? null : Timestamp.from(publishedAt));
        saveProductChildren(productId, request, false);
        audit(adminId, "PRODUCT_CREATE", "PRODUCT", productId.toString(), "상품 등록");
        return product(productId);
    }

    @Transactional
    public ProductDetail updateProduct(UUID productId, ProductUpsertRequest request, UUID adminId) {
        validateProductRequest(request);
        if (request.version() == null) {
            throw validation("version이 필요합니다.");
        }
        int updated = jdbcTemplate.update("""
                UPDATE products SET brand_id = ?, slug = ?, name = ?, short_description = ?, description = ?,
                       status = ?, featured = ?,
                       published_at = CASE WHEN ? = 'PUBLISHED' THEN coalesce(published_at, CURRENT_TIMESTAMP) ELSE published_at END,
                       updated_at = CURRENT_TIMESTAMP, version = version + 1
                 WHERE id = ? AND version = ?
                """, request.brandId(), request.slug(), request.name(), request.summary(), request.description(),
                request.status().toUpperCase(Locale.ROOT), request.featured(), request.status().toUpperCase(Locale.ROOT),
                productId, request.version());
        if (updated != 1) {
            throw optimisticConflict();
        }
        saveProductChildren(productId, request, true);
        audit(adminId, "PRODUCT_UPDATE", "PRODUCT", productId.toString(), "상품 수정");
        return product(productId);
    }

    @Transactional
    public void deleteProduct(UUID productId, Long version, boolean confirmOrderHistory, UUID adminId) {
        if (version == null) {
            throw validation("version이 필요합니다.");
        }

        List<ProductDeletionTarget> targets = jdbcTemplate.query("""
                SELECT id, slug, name, status, version
                  FROM products
                 WHERE id = ?
                   FOR NO KEY UPDATE
                """, (rs, rowNum) -> new ProductDeletionTarget(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getLong("version")
        ), productId);
        if (targets.isEmpty()) {
            throw notFound("상품");
        }

        ProductDeletionTarget target = targets.getFirst();
        if (target.version() != version) {
            throw optimisticConflict();
        }
        if (target.status().equals("PUBLISHED")) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_MUST_BE_UNPUBLISHED",
                    "판매 중인 상품은 삭제할 수 없습니다.",
                    "먼저 상품을 숨김 또는 판매 종료 상태로 변경한 뒤 다시 시도해 주세요."
            );
        }
        if (hasHomeProductLink(target.slug())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_IN_USE",
                    "홈 콘텐츠에서 사용 중인 상품입니다.",
                    "홈의 상품 직접 링크를 다른 대상으로 변경한 뒤 다시 시도해 주세요."
            );
        }

        // Order creation locks cart items before updating variants. Deletion uses the same
        // deterministic cart -> variant order so an in-flight checkout cannot form a lock cycle.
        // The product's NO KEY UPDATE lock remains compatible with the order's foreign-key check.
        List<UUID> cartItemIds = jdbcTemplate.query("""
                SELECT ci.id
                  FROM cart_items ci
                  JOIN product_variants pv ON pv.id = ci.variant_id
                 WHERE pv.product_id = ?
                 ORDER BY ci.id
                   FOR UPDATE OF ci
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), productId);
        List<UUID> variantIds = jdbcTemplate.query("""
                SELECT id
                  FROM product_variants
                 WHERE product_id = ?
                 ORDER BY id
                   FOR UPDATE
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), productId);
        ProductOrderReferences orderReferences = productOrderReferences(productId);
        if (orderReferences.activeReservationCount() > 0) {
            throw productHasActiveReservation();
        }
        if (orderReferences.hasHistory() && !confirmOrderHistory) {
            throw productHasOrderHistory();
        }

        int imageCount = count("product_images", "product_id", productId);
        int collectionLinkCount = count("collection_products", "product_id", productId);
        int wishlistItemCount = count("wishlist_items", "product_id", productId);
        int cartItemCount = cartItemIds.size();

        try {
            if (orderReferences.hasHistory()) {
                jdbcTemplate.update("""
                        UPDATE order_items
                           SET product_id = NULL, variant_id = NULL
                         WHERE product_id = ?
                            OR variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)
                        """, productId, productId);
                jdbcTemplate.update("""
                        UPDATE inventory_reservations
                           SET variant_id = NULL, updated_at = CURRENT_TIMESTAMP
                         WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)
                           AND status <> 'ACTIVE'
                        """, productId);
            }
            jdbcTemplate.update("""
                    DELETE FROM cart_items
                     WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)
                    """, productId);
            jdbcTemplate.update("DELETE FROM wishlist_items WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM collection_products WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM product_images WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM product_categories WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM product_species WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM product_variants WHERE product_id = ?", productId);
            int deleted = jdbcTemplate.update("DELETE FROM products WHERE id = ? AND version = ?", productId, version);
            if (deleted != 1) {
                throw optimisticConflict();
            }
        } catch (DataIntegrityViolationException | TransientDataAccessException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_DELETE_CONFLICT",
                    "상품 참조 데이터가 변경되어 삭제하지 못했습니다.",
                    "주문·장바구니 상태를 확인하고 화면을 새로고침한 뒤 다시 시도해 주세요."
            );
        }

        auditProductDeletion(adminId, target, variantIds.size(), imageCount, collectionLinkCount,
                cartItemCount, wishlistItemCount, confirmOrderHistory, orderReferences);
    }

    @Transactional
    public ProductDetail changeProductStatus(UUID productId, String requestedStatus, long version, UUID adminId) {
        String status = requestedStatus.toUpperCase(Locale.ROOT);
        if (!PRODUCT_STATUSES.contains(status)) {
            throw validation("지원하지 않는 상품 상태입니다.");
        }
        if (status.equals("PUBLISHED")) {
            List<String> imageKeys = jdbcTemplate.query(
                    "SELECT storage_key FROM product_images WHERE product_id = ?",
                    (rs, rowNum) -> rs.getString(1),
                    productId
            );
            if (imageKeys.isEmpty() || imageKeys.stream().anyMatch(key -> !isValidStoredImage(key))) {
                throw validation("상품을 공개하려면 업로드가 완료된 이미지가 한 장 이상 필요합니다.");
            }
        }
        int updated = jdbcTemplate.update("""
                UPDATE products SET status = ?,
                       published_at = CASE WHEN ? = 'PUBLISHED' THEN coalesce(published_at, CURRENT_TIMESTAMP) ELSE published_at END,
                       updated_at = CURRENT_TIMESTAMP, version = version + 1
                 WHERE id = ? AND version = ?
                """, status, status, productId, version);
        if (updated != 1) {
            throw optimisticConflict();
        }
        audit(adminId, "PRODUCT_STATUS_CHANGE", "PRODUCT", productId.toString(), status);
        return product(productId);
    }

    @Transactional
    public Variant changeStock(UUID variantId, StockRequest request, UUID adminId) {
        int updated = jdbcTemplate.update("""
                UPDATE product_variants SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                 WHERE id = ? AND version = ?
                """, request.stockQuantity(), variantId, request.version());
        if (updated != 1) {
            throw optimisticConflict();
        }
        audit(adminId, "INVENTORY_CHANGE", "PRODUCT_VARIANT", variantId.toString(),
                "재고=" + request.stockQuantity());
        return jdbcTemplate.queryForObject("""
                SELECT id, sku, name, price, stock_quantity, status, sort_order, version
                  FROM product_variants WHERE id = ?
                """, (rs, rowNum) -> new Variant(rs.getObject("id", UUID.class), rs.getString("sku"),
                rs.getString("name"), rs.getLong("price"), rs.getInt("stock_quantity"), rs.getString("status"),
                rs.getInt("sort_order"), rs.getLong("version")), variantId);
    }

    @Transactional(readOnly = true)
    public List<HomeSection> homeSections() {
        return jdbcTemplate.query("""
                SELECT id, section_key, title, content::text AS content, sort_order, version, updated_at
                  FROM home_sections ORDER BY sort_order
                """, (rs, rowNum) -> new HomeSection(rs.getObject("id", UUID.class), rs.getString("section_key"),
                rs.getString("title"), rs.getString("content"), rs.getInt("sort_order"), rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant()));
    }

    @Transactional
    public HomeSection updateHomeSection(UUID sectionId, HomeSectionUpdate request, UUID adminId) {
        validateHomeSectionContent(sectionId, request.content());
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE home_sections SET title = ?, content = CAST(? AS jsonb), sort_order = ?,
                           updated_at = CURRENT_TIMESTAMP, version = version + 1
                     WHERE id = ? AND version = ?
                    """, request.title(), request.content(), request.sortOrder(), sectionId, request.version());
            if (updated != 1) {
                throw optimisticConflict();
            }
        } catch (DataAccessException exception) {
            throw validation("홈 콘텐츠 JSON 또는 노출 순서를 확인해 주세요.");
        }
        audit(adminId, "HOME_SECTION_UPDATE", "HOME_SECTION", sectionId.toString(), "홈 섹션 수정");
        return homeSections().stream().filter(section -> section.id().equals(sectionId)).findFirst()
                .orElseThrow(() -> notFound("홈 섹션"));
    }

    @Transactional(readOnly = true)
    public List<HeroSlide> heroSlides() {
        return jdbcTemplate.query("""
                SELECT id, title, description, image_storage_key, image_alt_text, link_type, link_value,
                       status, sort_order, version
                  FROM home_hero_slides ORDER BY sort_order
                """, (rs, rowNum) -> new HeroSlide(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("description"), rs.getString("image_storage_key"), rs.getString("image_alt_text"),
                rs.getString("link_type"), rs.getString("link_value"), rs.getString("status"),
                rs.getInt("sort_order"), rs.getLong("version")));
    }

    @Transactional
    public HeroSlide updateHeroSlide(UUID slideId, HeroSlideUpdate request, UUID adminId) {
        String status = request.status().toUpperCase(Locale.ROOT);
        String linkType = request.linkType().toUpperCase(Locale.ROOT);
        String linkValue = request.linkValue().trim();
        if (!Set.of("DRAFT", "PUBLISHED", "HIDDEN").contains(status)
                || !Set.of("COLLECTION", "PRODUCT", "CONTENT", "HELP").contains(linkType)
                || request.sortOrder() > 3) {
            throw validation("히어로 공개 상태, 링크 유형 또는 슬롯 순서를 확인해 주세요.");
        }
        lockProductLinkTarget(linkType, linkValue);
        int updated = jdbcTemplate.update("""
                UPDATE home_hero_slides
                   SET title = ?, description = ?, image_storage_key = ?, image_alt_text = ?,
                       link_type = ?, link_value = ?, status = ?, sort_order = ?,
                       published_at = CASE WHEN ? = 'PUBLISHED' THEN coalesce(published_at, CURRENT_TIMESTAMP) ELSE published_at END,
                       updated_at = CURRENT_TIMESTAMP, version = version + 1
                 WHERE id = ? AND version = ?
                """, request.title(), request.description(), request.imageStorageKey(), request.imageAlt(),
                linkType, linkValue, status, request.sortOrder(), status, slideId, request.version());
        if (updated != 1) {
            throw optimisticConflict();
        }
        audit(adminId, "HERO_SLIDE_UPDATE", "HERO_SLIDE", slideId.toString(), "히어로 슬라이드 수정");
        return heroSlides().stream().filter(slide -> slide.id().equals(slideId)).findFirst()
                .orElseThrow(() -> notFound("히어로 슬라이드"));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummary> orders(String q, String status, int page, int size) {
        String search = q == null ? "" : q.trim();
        String state = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String where = "WHERE (? = '' OR o.order_number ILIKE '%' || ? || '%' OR o.buyer_name ILIKE '%' || ? || '%') "
                + "AND (? = '' OR o.order_status = ?)";
        long total = jdbcTemplate.queryForObject("SELECT count(*) FROM orders o " + where, Long.class,
                search, search, search, state, state);
        List<AdminOrderSummary> data = jdbcTemplate.query("""
                SELECT order_number, order_type, buyer_name, order_status, payment_status, total_amount, ordered_at
                  FROM orders o
                """ + where + " ORDER BY ordered_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminOrderSummary(rs.getString("order_number"), rs.getString("order_type"),
                        rs.getString("buyer_name"), rs.getString("order_status"), rs.getString("payment_status"),
                        rs.getLong("total_amount"), rs.getTimestamp("ordered_at").toInstant()),
                search, search, search, state, state, size, Math.multiplyExact(page, size));
        return new PageResponse<>(data, page(page, size, total));
    }

    @Transactional(readOnly = true)
    public AdminOrderDetail order(String orderNumber) {
        List<Long> versions = jdbcTemplate.query("SELECT version FROM orders WHERE order_number = ?",
                (rs, rowNum) -> rs.getLong(1), orderNumber);
        if (versions.isEmpty()) {
            throw notFound("주문");
        }
        return new AdminOrderDetail(orderService.detailForPayment(orderNumber), versions.getFirst());
    }

    @Transactional
    public AdminOrderDetail transition(String orderNumber, TransitionRequest request, UUID adminId) {
        List<OrderState> states = jdbcTemplate.query("""
                SELECT id, order_status, version FROM orders WHERE order_number = ? FOR UPDATE
                """, (rs, rowNum) -> new OrderState(rs.getObject("id", UUID.class), rs.getString("order_status"),
                rs.getLong("version")), orderNumber);
        if (states.isEmpty()) {
            throw notFound("주문");
        }
        OrderState state = states.getFirst();
        if (state.version() != request.version()) {
            throw optimisticConflict();
        }
        String target = request.toStatus().toUpperCase(Locale.ROOT);
        if (!allowedTransition(state.status(), target)) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_TRANSITION_NOT_ALLOWED", "주문 상태를 변경할 수 없습니다.",
                    state.status() + " 상태에서 " + target + " 상태로 변경할 수 없습니다.");
        }
        if (state.status().equals("PENDING_PAYMENT") && target.equals("CANCELLED")) {
            releaseActiveReservations(state.id());
            jdbcTemplate.update("""
                    UPDATE payments SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                     WHERE order_id = ? AND status = 'READY'
                    """, state.id());
        }
        jdbcTemplate.update("""
                UPDATE orders SET order_status = ?,
                       payment_status = CASE WHEN ? = 'CANCELLED' AND payment_status = 'READY' THEN 'CANCELLED' ELSE payment_status END,
                       cancelled_at = CASE WHEN ? = 'CANCELLED' THEN CURRENT_TIMESTAMP ELSE cancelled_at END,
                       updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ?
                """, target, target, target, state.id());
        jdbcTemplate.update("""
                INSERT INTO order_status_history (id, order_id, from_status, to_status, reason, changed_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), state.id(), state.status(), target, request.reason(), adminId);
        audit(adminId, "ORDER_TRANSITION", "ORDER", orderNumber, state.status() + " -> " + target);
        return order(orderNumber);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> users(String q, int page, int size) {
        String search = q == null ? "" : q.trim();
        String where = "WHERE (? = '' OR u.email ILIKE '%' || ? || '%' OR u.name ILIKE '%' || ? || '%')";
        long total = jdbcTemplate.queryForObject("SELECT count(*) FROM users u " + where, Long.class,
                search, search, search);
        List<UserSummary> data = jdbcTemplate.query("""
                SELECT u.id, u.email, u.name, u.phone, u.status, u.created_at,
                       count(o.id) AS order_count,
                       coalesce(sum(o.total_amount) FILTER (WHERE o.order_status <> 'CANCELLED'), 0) AS total_purchased
                  FROM users u LEFT JOIN orders o ON o.user_id = u.id
                """ + where + " GROUP BY u.id ORDER BY u.created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new UserSummary(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("name"), rs.getString("phone"), rs.getString("status"),
                        rs.getLong("order_count"), rs.getLong("total_purchased"),
                        rs.getTimestamp("created_at").toInstant()), search, search, search, size,
                Math.multiplyExact(page, size));
        return new PageResponse<>(data, page(page, size, total));
    }

    @Transactional(readOnly = true)
    public List<ReferenceItem> references(String type) {
        return switch (type) {
            case "brands" -> jdbcTemplate.query(
                    "SELECT id, slug AS code, name FROM brands ORDER BY name",
                    (rs, rowNum) -> new ReferenceItem(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")));
            case "categories" -> jdbcTemplate.query(
                    "SELECT id, slug AS code, name FROM categories ORDER BY sort_order, name",
                    (rs, rowNum) -> new ReferenceItem(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")));
            case "species" -> jdbcTemplate.query(
                    "SELECT id, code, name FROM species ORDER BY sort_order, name",
                    (rs, rowNum) -> new ReferenceItem(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")));
            default -> throw validation("지원하지 않는 분류입니다.");
        };
    }

    private void saveProductChildren(UUID productId, ProductUpsertRequest request, boolean updatingProduct) {
        for (AdminApiModels.VariantInput variant : request.variants()) {
            if (variant.id() == null) {
                if (variant.version() != null) {
                    throw validation("새 옵션에는 id와 version을 지정할 수 없습니다.");
                }
                jdbcTemplate.update("""
                        INSERT INTO product_variants (id, product_id, sku, name, price, stock_quantity, status, sort_order)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), productId, variant.sku(), variant.optionLabel(), variant.price(),
                        variant.stockQuantity(), variant.status().toUpperCase(Locale.ROOT), variant.sortOrder());
                continue;
            }
            if (!updatingProduct) {
                throw validation("새 상품의 옵션에는 id와 version을 지정할 수 없습니다.");
            }
            if (variant.version() == null) {
                throw validation("기존 옵션의 version이 필요합니다.");
            }
            int updated = jdbcTemplate.update("""
                    UPDATE product_variants SET sku = ?, name = ?, price = ?, stock_quantity = ?, status = ?,
                           sort_order = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                     WHERE id = ? AND product_id = ? AND version = ?
                    """, variant.sku(), variant.optionLabel(), variant.price(), variant.stockQuantity(),
                    variant.status().toUpperCase(Locale.ROOT), variant.sortOrder(), variant.id(), productId,
                    variant.version());
            if (updated != 1) {
                throw optimisticConflict();
            }
        }
        jdbcTemplate.update("DELETE FROM product_categories WHERE product_id = ?", productId);
        safeList(request.categoryIds()).forEach(categoryId -> jdbcTemplate.update(
                "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)", productId, categoryId));
        jdbcTemplate.update("DELETE FROM product_species WHERE product_id = ?", productId);
        safeList(request.speciesIds()).forEach(speciesId -> jdbcTemplate.update(
                "INSERT INTO product_species (product_id, species_id) VALUES (?, ?)", productId, speciesId));
        if (request.images() != null) {
            jdbcTemplate.update("DELETE FROM product_images WHERE product_id = ?", productId);
            request.images().forEach(image -> jdbcTemplate.update("""
                    INSERT INTO product_images (id, product_id, storage_key, alt_text, sort_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, image.id() == null ? UUID.randomUUID() : image.id(), productId,
                    image.storageKey(), image.alt(), image.sortOrder()));
        }
    }

    private void validateProductRequest(ProductUpsertRequest request) {
        if (!PRODUCT_STATUSES.contains(request.status().toUpperCase(Locale.ROOT))) {
            throw validation("지원하지 않는 상품 상태입니다.");
        }
        if (request.status().equalsIgnoreCase("PUBLISHED")
                && (request.images() == null || request.images().isEmpty())) {
            throw validation("상품을 공개하려면 이미지가 한 장 이상 필요합니다.");
        }
        if (request.images() != null) {
            Set<String> storageKeys = new java.util.HashSet<>();
            for (AdminApiModels.ImageInput image : request.images()) {
                if (!isCanonicalStorageKey(image.storageKey())) {
                    throw validation("상품 이미지 저장 경로 형식이 올바르지 않습니다.");
                }
                if (!storageKeys.add(image.storageKey())) {
                    throw validation("같은 이미지를 상품에 두 번 등록할 수 없습니다.");
                }
                if (!isValidStoredImage(image.storageKey())) {
                    throw validation("업로드되지 않았거나 지원하지 않는 상품 이미지가 포함되어 있습니다.");
                }
            }
        }
        if (request.variants().stream().anyMatch(variant ->
                !VARIANT_STATUSES.contains(variant.status().toUpperCase(Locale.ROOT)))) {
            throw validation("지원하지 않는 옵션 상태가 포함되어 있습니다.");
        }
    }

    private boolean isValidStoredImage(String storageKey) {
        return storageService.find(storageKey)
                .map(stored -> Set.of("image/jpeg", "image/png", "image/webp")
                        .contains(stored.contentType().toString()))
                .orElse(false);
    }

    private boolean isCanonicalStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\0') >= 0
                || storageKey.indexOf('\\') >= 0 || storageKey.startsWith("/")
                || storageKey.endsWith("/") || storageKey.contains("//")
                || !SAFE_STORAGE_KEY.matcher(storageKey).matches()) {
            return false;
        }
        try {
            Path path = Path.of(storageKey);
            if (path.isAbsolute()) {
                return false;
            }
            for (Path segment : path) {
                if (segment.toString().equals(".") || segment.toString().equals("..")) {
                    return false;
                }
            }
            return true;
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private ProductOrderReferences productOrderReferences(UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT count(*)
                       FROM order_items oi
                      WHERE oi.product_id = ?
                         OR oi.variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)
                    ) AS order_item_count,
                    (SELECT count(*)
                       FROM inventory_reservations ir
                      WHERE ir.variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)
                    ) AS reservation_count,
                    (SELECT count(*)
                       FROM inventory_reservations ir
                      WHERE ir.variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)
                        AND ir.status = 'ACTIVE'
                    ) AS active_reservation_count
                """, (rs, rowNum) -> new ProductOrderReferences(
                rs.getInt("order_item_count"),
                rs.getInt("reservation_count"),
                rs.getInt("active_reservation_count")
        ), productId, productId, productId, productId);
    }

    private boolean hasHomeProductLink(String slug) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM home_hero_slides
                     WHERE link_type = 'PRODUCT' AND btrim(link_value) = ?
                    UNION ALL
                    SELECT 1 FROM home_lifestyle_contents
                     WHERE link_type = 'PRODUCT' AND btrim(link_value) = ?
                    UNION ALL
                    SELECT 1 FROM home_sections
                     WHERE content ->> 'linkType' = 'PRODUCT'
                       AND btrim(content ->> 'linkValue') = ?
                )
                """, Boolean.class, slug, slug, slug));
    }

    private int count(String table, String column, UUID value) {
        // Callers only pass the fixed table and column names declared in this class.
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }

    private void validateHomeSectionContent(UUID sectionId, String content) {
        List<String> sectionKeys = jdbcTemplate.query(
                "SELECT section_key FROM home_sections WHERE id = ?",
                (rs, rowNum) -> rs.getString(1),
                sectionId
        );
        if (sectionKeys.isEmpty()) {
            throw notFound("홈 섹션");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JacksonException exception) {
            throw validation("홈 콘텐츠는 올바른 JSON 객체여야 합니다.");
        }
        if (root == null || !root.isObject()) {
            throw validation("홈 콘텐츠는 JSON 객체여야 합니다.");
        }

        HomeSectionKey sectionKey = HomeSectionKey.valueOf(sectionKeys.getFirst());
        switch (sectionKey) {
            case ANNOUNCEMENT_HEADER -> validateAnnouncementContent(root);
            case SERVICE_GUIDE -> validateServiceGuideContent(root);
            default -> {
                // These sections currently source their public content from related tables.
                // Requiring an object keeps their JSONB shape compatible while allowing future fields.
            }
        }
    }

    private void validateAnnouncementContent(JsonNode root) {
        requiredText(root, "announcementText");
        String linkType = requiredText(root, "linkType");
        String linkValue = requiredText(root, "linkValue");
        try {
            MerchandisingLinkType.valueOf(linkType);
        } catch (IllegalArgumentException exception) {
            throw validation("공지 링크 유형을 확인해 주세요.");
        }
        lockProductLinkTarget(linkType, linkValue);
    }

    private void lockProductLinkTarget(String linkType, String linkValue) {
        if (!"PRODUCT".equals(linkType)) {
            return;
        }
        List<UUID> targets = jdbcTemplate.query("""
                SELECT id
                  FROM products
                 WHERE slug = ?
                   FOR SHARE
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), linkValue.trim());
        if (targets.isEmpty()) {
            throw validation("상품 링크 대상을 찾을 수 없습니다.");
        }
    }

    private void validateServiceGuideContent(JsonNode root) {
        requiredNonNegativeLong(root, "shippingFee");
        requiredNonNegativeLong(root, "freeShippingThreshold");
        JsonNode links = root.get("links");
        if (links == null || !links.isArray()) {
            throw validation("서비스 안내 links는 문자열 배열이어야 합니다.");
        }
        for (JsonNode link : links) {
            if (isNotNonBlankText(link)) {
                throw validation("서비스 안내 links는 문자열 배열이어야 합니다.");
            }
        }
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (isNotNonBlankText(value)) {
            throw validation("홈 콘텐츠 필드가 올바르지 않습니다: " + field);
        }
        return value.textValue();
    }

    private boolean isNotNonBlankText(JsonNode value) {
        return value == null || !value.isTextual() || value.textValue().isBlank();
    }

    private long requiredNonNegativeLong(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw validation("홈 콘텐츠 필드가 0 이상의 정수여야 합니다: " + field);
        }
        return value.longValue();
    }

    private void releaseActiveReservations(UUID orderId) {
        List<Reservation> reservations = jdbcTemplate.query("""
                SELECT id, variant_id, quantity FROM inventory_reservations
                 WHERE order_id = ? AND status = 'ACTIVE' FOR UPDATE
                """, (rs, rowNum) -> new Reservation(rs.getObject("id", UUID.class),
                rs.getObject("variant_id", UUID.class), rs.getInt("quantity")), orderId);
        for (Reservation reservation : reservations) {
            jdbcTemplate.update("UPDATE product_variants SET stock_quantity = stock_quantity + ?, version = version + 1 WHERE id = ?",
                    reservation.quantity(), reservation.variantId());
            jdbcTemplate.update("UPDATE inventory_reservations SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    reservation.id());
        }
    }

    private boolean allowedTransition(String from, String to) {
        return switch (from) {
            case "PENDING_PAYMENT" -> to.equals("CANCELLED");
            case "PAID" -> to.equals("PREPARING");
            case "PREPARING" -> to.equals("SHIPPED");
            case "SHIPPED" -> to.equals("DELIVERED");
            default -> false;
        };
    }

    private void audit(UUID adminId, String action, String resourceType, String resourceId, String reason) {
        jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (id, admin_user_id, action, resource_type, resource_id, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), adminId, action, resourceType, resourceId, reason);
    }

    private void auditProductDeletion(
            UUID adminId,
            ProductDeletionTarget target,
            int variantCount,
            int imageCount,
            int collectionLinkCount,
            int cartItemCount,
            int wishlistItemCount,
            boolean orderHistoryConfirmed,
            ProductOrderReferences orderReferences
    ) {
        jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (
                    id, admin_user_id, action, resource_type, resource_id, before_summary, reason
                ) VALUES (
                    ?, ?, 'PRODUCT_DELETE', 'PRODUCT', ?,
                    jsonb_build_object(
                        'id', ?, 'slug', ?, 'name', ?, 'status', ?, 'version', ?,
                        'variantCount', ?, 'imageCount', ?, 'collectionLinkCount', ?,
                        'cartItemCount', ?, 'wishlistItemCount', ?,
                        'orderHistoryConfirmed', ?, 'orderItemCount', ?,
                        'reservationCount', ?
                    ),
                    ?
                )
                """, UUID.randomUUID(), adminId, target.id().toString(), target.id().toString(), target.slug(),
                target.name(), target.status(), target.version(), variantCount, imageCount, collectionLinkCount,
                cartItemCount, wishlistItemCount, orderHistoryConfirmed, orderReferences.orderItemCount(),
                orderReferences.reservationCount(), orderHistoryConfirmed
                        ? "주문 이력 스냅샷을 보존하고 현재 상품 연결을 분리한 뒤 삭제"
                        : "주문 이력이 없는 비판매 상품 삭제");
    }

    private PageMetadata page(int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageMetadata(page, size, total, totalPages, page == 0, page + 1 >= totalPages);
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private ApiException validation(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.", detail);
    }

    private ApiException optimisticConflict() {
        return new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "다른 관리자가 먼저 수정했습니다.",
                "화면을 새로고침한 뒤 다시 시도해 주세요.");
    }

    private ApiException productHasOrderHistory() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PRODUCT_HAS_ORDER_HISTORY",
                "주문 이력이 있는 상품입니다.",
                "주문·결제·상품 스냅샷은 유지되지만 현재 상품 연결은 분리됩니다. 계속 삭제하려면 주문 이력 포함 삭제를 한 번 더 확인해 주세요."
        );
    }

    private ApiException productHasActiveReservation() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PRODUCT_HAS_ACTIVE_RESERVATION",
                "결제 진행 중인 재고 예약이 있어 상품을 삭제할 수 없습니다.",
                "주문이 결제·취소·만료 처리되어 재고 예약이 종료된 뒤 다시 시도해 주세요."
        );
    }

    private ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + "을(를) 찾을 수 없습니다.",
                "식별자와 접근 권한을 확인해 주세요.");
    }

    private record ProductDetailBase(
            UUID id, UUID brandId, String slug, String name, String summary, String description,
            String status, boolean featured, long version
    ) {
    }

    private record ProductDeletionTarget(UUID id, String slug, String name, String status, long version) {
    }

    private record ProductOrderReferences(
            int orderItemCount,
            int reservationCount,
            int activeReservationCount
    ) {
        private boolean hasHistory() {
            return orderItemCount > 0 || reservationCount > 0;
        }
    }

    private record OrderState(UUID id, String status, long version) {
    }

    private record Reservation(UUID id, UUID variantId, int quantity) {
    }
}
