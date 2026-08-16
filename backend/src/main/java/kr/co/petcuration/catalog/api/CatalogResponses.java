package kr.co.petcuration.catalog.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogResponses {

    private CatalogResponses() {
    }

    public record ProductListResponse(List<ProductCardResponse> data, PageMetadata page) {
        public ProductListResponse {
            data = List.copyOf(data);
        }
    }

    public record PageMetadata(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }

    public record ProductDetailEnvelope(ProductDetailResponse data) {
    }

    public record ProductCardResponse(
            UUID id,
            String slug,
            String name,
            String summary,
            BrandResponse brand,
            Long listPrice,
            Long salePrice,
            String currency,
            boolean inStock,
            boolean wishlisted,
            boolean featured,
            ImageResponse thumbnail,
            List<TaxonomyResponse> species,
            List<TaxonomyResponse> categories
    ) {
    }

    public record ProductDetailResponse(
            UUID id,
            String slug,
            String name,
            String summary,
            String description,
            BrandResponse brand,
            String currency,
            boolean wishlisted,
            Map<String, Object> attributes,
            List<ImageResponse> images,
            List<VariantResponse> variants,
            List<TaxonomyResponse> species,
            List<TaxonomyResponse> categories
    ) {
    }

    public record BrandResponse(UUID id, String slug, String name) {
    }

    public record TaxonomyResponse(String slug, String name) {
    }

    public record ImageResponse(String url, String alt, int sortOrder) {
    }

    public record VariantResponse(
            UUID id,
            String sku,
            String optionLabel,
            long listPrice,
            long salePrice,
            int stockQuantity,
            boolean purchasable,
            int maxPurchaseQuantity
    ) {
    }
}
