package kr.co.petcuration.catalog.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.co.petcuration.catalog.api.CatalogResponses.BrandResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.ImageResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.PageMetadata;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductCardResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductDetailResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductListResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.TaxonomyResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.VariantResponse;
import kr.co.petcuration.catalog.domain.CatalogReferenceStatus;
import kr.co.petcuration.catalog.domain.ProductStatus;
import kr.co.petcuration.catalog.domain.VariantStatus;
import kr.co.petcuration.catalog.infrastructure.BrandEntity;
import kr.co.petcuration.catalog.infrastructure.BrandRepository;
import kr.co.petcuration.catalog.infrastructure.CategoryEntity;
import kr.co.petcuration.catalog.infrastructure.CategoryRepository;
import kr.co.petcuration.catalog.infrastructure.ProductCatalogSpecifications;
import kr.co.petcuration.catalog.infrastructure.ProductEntity;
import kr.co.petcuration.catalog.infrastructure.ProductImageEntity;
import kr.co.petcuration.catalog.infrastructure.ProductRepository;
import kr.co.petcuration.catalog.infrastructure.ProductVariantEntity;
import kr.co.petcuration.catalog.infrastructure.SpeciesEntity;
import kr.co.petcuration.catalog.infrastructure.SpeciesRepository;
import kr.co.petcuration.common.api.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final SpeciesRepository speciesRepository;

    public CatalogQueryService(
            ProductRepository productRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            SpeciesRepository speciesRepository
    ) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.speciesRepository = speciesRepository;
    }

    public ProductListResponse findProducts(
            String query,
            String brandSlug,
            String categorySlug,
            String speciesSlug,
            boolean inStockOnly,
            int pageNumber,
            int pageSize,
            String sort
    ) {
        Page<ProductEntity> page = productRepository
                .findAll(
                        ProductCatalogSpecifications.publicCatalog(
                                normalizeQuery(query),
                                brandSlug,
                                categorySlug,
                                speciesSlug,
                                inStockOnly,
                                Instant.now()
                        ),
                        PageRequest.of(pageNumber, pageSize, toSort(sort))
                );
        List<ProductCardResponse> items = page.getContent()
                .stream()
                .map(this::toCard)
                .toList();
        return new ProductListResponse(items, new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        ));
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.strip();
    }

    private Sort toSort(String sort) {
        return switch (sort) {
            case "newest,desc" -> Sort.by(
                    Sort.Order.desc("publishedAt").nullsLast(),
                    Sort.Order.asc("id")
            );
            case "price,asc" -> Sort.by(
                    Sort.Order.asc("minimumActivePrice").nullsLast(),
                    Sort.Order.asc("name"),
                    Sort.Order.asc("id")
            );
            case "price,desc" -> Sort.by(
                    Sort.Order.desc("minimumActivePrice").nullsLast(),
                    Sort.Order.asc("name"),
                    Sort.Order.asc("id")
            );
            case "name,asc" -> Sort.by(
                    Sort.Order.asc("name"),
                    Sort.Order.asc("id")
            );
            default -> throw new IllegalArgumentException("Unsupported product sort: " + sort);
        };
    }

    public ProductDetailResponse findProduct(String slug) {
        ProductEntity product = productRepository
                .findBySlugAndStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
                        slug,
                        ProductStatus.PUBLISHED,
                        Instant.now()
                )
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품을 찾을 수 없습니다."));
        return toDetail(product);
    }

    public List<ProductCardResponse> findPublicProductCards(Instant publicAt) {
        return productRepository.findAll(
                        ProductCatalogSpecifications.publicCatalog(
                                null,
                                null,
                                null,
                                null,
                                false,
                                publicAt
                        ),
                        Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id"))
                )
                .stream()
                .map(this::toCard)
                .toList();
    }

    public List<ProductCardResponse> findPublicProductCards(List<java.util.UUID> productIds, Instant publicAt) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<java.util.UUID, ProductCardResponse> cardsById = productRepository
                .findAll(ProductCatalogSpecifications.publicProductsByIds(productIds, publicAt))
                .stream()
                .map(this::toCard)
                .collect(java.util.stream.Collectors.toMap(ProductCardResponse::id, card -> card));

        return productIds.stream()
                .map(cardsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<BrandResponse> findActiveBrands() {
        return brandRepository.findByStatusOrderByNameAscIdAsc(CatalogReferenceStatus.ACTIVE)
                .stream()
                .map(this::toBrand)
                .toList();
    }

    public List<TaxonomyResponse> findActiveCategories() {
        return categoryRepository.findByStatusOrderBySortOrderAscIdAsc(CatalogReferenceStatus.ACTIVE)
                .stream()
                .map(this::toCategory)
                .toList();
    }

    public List<TaxonomyResponse> findActiveSpecies() {
        return speciesRepository.findByStatusOrderBySortOrderAscIdAsc(CatalogReferenceStatus.ACTIVE)
                .stream()
                .map(this::toSpecies)
                .toList();
    }

    private ProductCardResponse toCard(ProductEntity product) {
        List<ProductVariantEntity> activeVariants = activeVariants(product);
        Long priceFrom = activeVariants.stream()
                .map(ProductVariantEntity::getPrice)
                .min(Comparator.naturalOrder())
                .orElse(null);
        boolean soldOut = activeVariants.isEmpty()
                || activeVariants.stream().noneMatch(variant -> variant.getStockQuantity() > 0);
        ImageResponse thumbnail = product.getImages().stream()
                .findFirst()
                .map(this::toImage)
                .orElse(null);

        return new ProductCardResponse(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getShortDescription(),
                toBrand(product.getBrand()),
                priceFrom,
                priceFrom,
                "KRW",
                !soldOut,
                false,
                product.isFeatured(),
                thumbnail,
                product.getSpecies().stream().map(this::toSpecies).toList(),
                product.getCategories().stream().map(this::toCategory).toList()
        );
    }

    private ProductDetailResponse toDetail(ProductEntity product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getShortDescription(),
                product.getDescription(),
                toBrand(product.getBrand()),
                "KRW",
                false,
                product.getAttributes() == null ? java.util.Map.of() : java.util.Map.copyOf(product.getAttributes()),
                product.getImages().stream().map(this::toImage).toList(),
                activeVariants(product).stream().map(this::toVariant).toList(),
                product.getSpecies().stream().map(this::toSpecies).toList(),
                product.getCategories().stream().map(this::toCategory).toList()
        );
    }

    private List<ProductVariantEntity> activeVariants(ProductEntity product) {
        return product.getVariants().stream()
                .filter(variant -> variant.getStatus() == VariantStatus.ACTIVE)
                .toList();
    }

    private BrandResponse toBrand(BrandEntity brand) {
        return new BrandResponse(brand.getId(), brand.getSlug(), brand.getName());
    }

    private ImageResponse toImage(ProductImageEntity image) {
        return new ImageResponse("/media/" + image.getStorageKey(), image.getAltText(), image.getSortOrder());
    }

    private VariantResponse toVariant(ProductVariantEntity variant) {
        return new VariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getPrice(),
                variant.getStockQuantity(),
                variant.getStockQuantity() > 0,
                Math.min(variant.getStockQuantity(), 10)
        );
    }

    private TaxonomyResponse toSpecies(SpeciesEntity species) {
        return new TaxonomyResponse(species.getCode().toLowerCase(java.util.Locale.ROOT), species.getName());
    }

    private TaxonomyResponse toCategory(CategoryEntity category) {
        return new TaxonomyResponse(category.getSlug(), category.getName());
    }
}
