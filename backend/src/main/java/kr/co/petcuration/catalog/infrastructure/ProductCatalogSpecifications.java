package kr.co.petcuration.catalog.infrastructure;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import kr.co.petcuration.catalog.domain.ProductStatus;
import kr.co.petcuration.catalog.domain.VariantStatus;
import org.springframework.data.jpa.domain.Specification;

public final class ProductCatalogSpecifications {

    private ProductCatalogSpecifications() {
    }

    public static Specification<ProductEntity> publicCatalog(
            String query,
            String brandSlug,
            String categorySlug,
            String speciesSlug,
            boolean inStockOnly,
            Instant publishedAtInclusive
    ) {
        Specification<ProductEntity> specification = isPublicAt(publishedAtInclusive);

        if (query != null) {
            specification = specification.and(matchesQuery(query));
        }
        if (brandSlug != null) {
            specification = specification.and(hasBrand(brandSlug));
        }
        if (categorySlug != null) {
            specification = specification.and(hasCategory(categorySlug));
        }
        if (speciesSlug != null) {
            specification = specification.and(hasSpecies(speciesSlug));
        }
        if (inStockOnly) {
            specification = specification.and(hasPurchasableVariant());
        }

        return specification;
    }

    public static Specification<ProductEntity> publicProductsByIds(
            Collection<UUID> productIds,
            Instant publishedAtInclusive
    ) {
        return isPublicAt(publishedAtInclusive)
                .and((root, criteriaQuery, criteriaBuilder) -> root.get("id").in(productIds));
    }

    private static Specification<ProductEntity> isPublicAt(Instant publishedAtInclusive) {
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("status"), ProductStatus.PUBLISHED),
                criteriaBuilder.isNotNull(root.get("publishedAt")),
                criteriaBuilder.lessThanOrEqualTo(root.get("publishedAt"), publishedAtInclusive)
        );
    }

    private static Specification<ProductEntity> matchesQuery(String query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.join("brand", JoinType.INNER).get("name")),
                            pattern,
                            '\\'
                    )
            );
        };
    }

    private static Specification<ProductEntity> hasBrand(String brandSlug) {
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(
                root.join("brand", JoinType.INNER).get("slug"),
                brandSlug
        );
    }

    private static Specification<ProductEntity> hasCategory(String categorySlug) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            Subquery<Integer> subquery = criteriaQuery.subquery(Integer.class);
            Root<ProductEntity> correlatedProduct = subquery.correlate(root);
            subquery.select(criteriaBuilder.literal(1));
            subquery.where(criteriaBuilder.equal(
                    correlatedProduct.join("categories", JoinType.INNER).get("slug"),
                    categorySlug
            ));
            return criteriaBuilder.exists(subquery);
        };
    }

    private static Specification<ProductEntity> hasSpecies(String speciesSlug) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            Subquery<Integer> subquery = criteriaQuery.subquery(Integer.class);
            Root<ProductEntity> correlatedProduct = subquery.correlate(root);
            subquery.select(criteriaBuilder.literal(1));
            subquery.where(criteriaBuilder.equal(
                    criteriaBuilder.lower(correlatedProduct.join("species", JoinType.INNER).get("code")),
                    speciesSlug
            ));
            return criteriaBuilder.exists(subquery);
        };
    }

    private static Specification<ProductEntity> hasPurchasableVariant() {
        return (root, criteriaQuery, criteriaBuilder) -> {
            Subquery<Integer> subquery = criteriaQuery.subquery(Integer.class);
            Root<ProductVariantEntity> variant = subquery.from(ProductVariantEntity.class);
            subquery.select(criteriaBuilder.literal(1));
            subquery.where(
                    criteriaBuilder.equal(variant.get("product"), root),
                    criteriaBuilder.equal(variant.get("status"), VariantStatus.ACTIVE),
                    criteriaBuilder.greaterThan(variant.get("stockQuantity"), 0)
            );
            return criteriaBuilder.exists(subquery);
        };
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
