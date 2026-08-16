package kr.co.petcuration.merchandising.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.catalog.api.CatalogResponses.BrandResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.PageMetadata;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductCardResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.TaxonomyResponse;
import kr.co.petcuration.merchandising.domain.MerchandisingLinkType;

public final class MerchandisingResponses {

    private MerchandisingResponses() {
    }

    public record HomeEnvelope(HomeResponse data) {
    }

    public record HomeResponse(
            AnnouncementResponse announcement,
            List<HeroSlideResponse> heroSlides,
            List<CollectionCardResponse> featuredCollections,
            List<ProductCardResponse> popularProducts,
            List<ProductCardResponse> newProducts,
            ExploreResponse explore,
            List<LifestyleContentResponse> lifestyleContents,
            ServiceGuideResponse serviceGuide
    ) {
        public HomeResponse {
            heroSlides = List.copyOf(heroSlides);
            featuredCollections = List.copyOf(featuredCollections);
            popularProducts = List.copyOf(popularProducts);
            newProducts = List.copyOf(newProducts);
            lifestyleContents = List.copyOf(lifestyleContents);
        }
    }

    public record AnnouncementResponse(String text, LinkResponse link) {
    }

    public record HeroSlideResponse(
            UUID id,
            String title,
            String description,
            MediaResponse image,
            LinkResponse link,
            int sortOrder
    ) {
    }

    public record CollectionListResponse(List<CollectionCardResponse> data, PageMetadata page) {
        public CollectionListResponse {
            data = List.copyOf(data);
        }
    }

    public record CollectionDetailEnvelope(CollectionDetailResponse data) {
    }

    public record CollectionCardResponse(
            UUID id,
            String slug,
            String title,
            String description,
            MediaResponse heroImage,
            int sortOrder,
            Instant publishedAt
    ) {
    }

    public record CollectionDetailResponse(
            UUID id,
            String slug,
            String title,
            String description,
            MediaResponse heroImage,
            List<ProductCardResponse> products,
            Instant publishedAt
    ) {
        public CollectionDetailResponse {
            products = List.copyOf(products);
        }
    }

    public record ExploreResponse(
            List<TaxonomyResponse> species,
            List<TaxonomyResponse> categories,
            List<BrandResponse> brands
    ) {
        public ExploreResponse {
            species = List.copyOf(species);
            categories = List.copyOf(categories);
            brands = List.copyOf(brands);
        }
    }

    public record LifestyleContentResponse(
            UUID id,
            String title,
            String description,
            MediaResponse image,
            LinkResponse link,
            int sortOrder
    ) {
    }

    public record ServiceGuideResponse(
            long shippingFee,
            long freeShippingThreshold,
            List<String> links
    ) {
        public ServiceGuideResponse {
            links = List.copyOf(links);
        }
    }

    public record LinkResponse(MerchandisingLinkType type, String value) {
    }

    public record MediaResponse(String url, String alt) {
    }
}
