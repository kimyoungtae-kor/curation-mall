package kr.co.petcuration.merchandising.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kr.co.petcuration.catalog.api.CatalogResponses.PageMetadata;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductCardResponse;
import kr.co.petcuration.catalog.application.CatalogQueryService;
import kr.co.petcuration.common.api.ResourceNotFoundException;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.AnnouncementResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.CollectionCardResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.CollectionDetailResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.CollectionListResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.ExploreResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.HeroSlideResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.HomeResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.LifestyleContentResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.LinkResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.MediaResponse;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.ServiceGuideResponse;
import kr.co.petcuration.merchandising.domain.HomeSectionKey;
import kr.co.petcuration.merchandising.domain.MerchandisingLinkType;
import kr.co.petcuration.merchandising.domain.PublicationStatus;
import kr.co.petcuration.merchandising.infrastructure.CollectionEntity;
import kr.co.petcuration.merchandising.infrastructure.CollectionRepository;
import kr.co.petcuration.merchandising.infrastructure.HomeHeroSlideEntity;
import kr.co.petcuration.merchandising.infrastructure.HomeHeroSlideRepository;
import kr.co.petcuration.merchandising.infrastructure.HomeLifestyleContentEntity;
import kr.co.petcuration.merchandising.infrastructure.HomeLifestyleContentRepository;
import kr.co.petcuration.merchandising.infrastructure.HomeSectionEntity;
import kr.co.petcuration.merchandising.infrastructure.HomeSectionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MerchandisingQueryService {

    private static final int HOME_COLLECTION_LIMIT = 6;
    private static final int HOME_PRODUCT_LIMIT = 8;

    private final CollectionRepository collectionRepository;
    private final HomeSectionRepository homeSectionRepository;
    private final HomeHeroSlideRepository homeHeroSlideRepository;
    private final HomeLifestyleContentRepository homeLifestyleContentRepository;
    private final CatalogQueryService catalogQueryService;

    public MerchandisingQueryService(
            CollectionRepository collectionRepository,
            HomeSectionRepository homeSectionRepository,
            HomeHeroSlideRepository homeHeroSlideRepository,
            HomeLifestyleContentRepository homeLifestyleContentRepository,
            CatalogQueryService catalogQueryService
    ) {
        this.collectionRepository = collectionRepository;
        this.homeSectionRepository = homeSectionRepository;
        this.homeHeroSlideRepository = homeHeroSlideRepository;
        this.homeLifestyleContentRepository = homeLifestyleContentRepository;
        this.catalogQueryService = catalogQueryService;
    }

    public HomeResponse findHome() {
        Instant publicAt = Instant.now();
        Map<HomeSectionKey, HomeSectionEntity> sections = homeSections();
        List<ProductCardResponse> publicProducts = catalogQueryService.findPublicProductCards(publicAt);

        List<HeroSlideResponse> heroSlides = homeHeroSlideRepository
                .findPublicAt(HomeSectionKey.HERO, PublicationStatus.PUBLISHED, publicAt)
                .stream()
                .map(this::toHeroSlide)
                .toList();
        List<CollectionCardResponse> featuredCollections = collectionRepository
                .findFeaturedPublicAt(
                        PublicationStatus.PUBLISHED,
                        publicAt,
                        PageRequest.of(0, HOME_COLLECTION_LIMIT)
                )
                .stream()
                .map(this::toCollectionCard)
                .toList();
        List<ProductCardResponse> popularProducts = publicProducts.stream()
                .filter(ProductCardResponse::featured)
                .limit(HOME_PRODUCT_LIMIT)
                .toList();
        List<ProductCardResponse> newProducts = publicProducts.stream()
                .limit(HOME_PRODUCT_LIMIT)
                .toList();
        List<LifestyleContentResponse> lifestyleContents = homeLifestyleContentRepository
                .findPublicAt(HomeSectionKey.LIFESTYLE, PublicationStatus.PUBLISHED, publicAt)
                .stream()
                .map(this::toLifestyleContent)
                .toList();

        return new HomeResponse(
                announcement(sectionContent(sections, HomeSectionKey.ANNOUNCEMENT_HEADER)),
                heroSlides,
                featuredCollections,
                popularProducts,
                newProducts,
                new ExploreResponse(
                        catalogQueryService.findActiveSpecies(),
                        catalogQueryService.findActiveCategories(),
                        catalogQueryService.findActiveBrands()
                ),
                lifestyleContents,
                serviceGuide(sectionContent(sections, HomeSectionKey.SERVICE_GUIDE))
        );
    }

    public CollectionListResponse findCollections(int pageNumber, int pageSize) {
        Page<CollectionEntity> page = collectionRepository.findPublicAt(
                PublicationStatus.PUBLISHED,
                Instant.now(),
                PageRequest.of(pageNumber, pageSize)
        );
        return new CollectionListResponse(
                page.getContent().stream().map(this::toCollectionCard).toList(),
                new PageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isFirst(),
                        page.isLast()
                )
        );
    }

    public CollectionDetailResponse findCollection(String slug) {
        Instant publicAt = Instant.now();
        CollectionEntity collection = collectionRepository
                .findPublicBySlug(slug, PublicationStatus.PUBLISHED, publicAt)
                .orElseThrow(() -> new ResourceNotFoundException("공개 중인 기획전을 찾을 수 없습니다."));
        List<java.util.UUID> productIds = collection.getProductLinks().stream()
                .map(link -> link.getProductId())
                .toList();

        return new CollectionDetailResponse(
                collection.getId(),
                collection.getSlug(),
                collection.getTitle(),
                collection.getDescription(),
                media(collection.getHeroStorageKey(), collection.getHeroAltText()),
                catalogQueryService.findPublicProductCards(productIds, publicAt),
                collection.getPublishedAt()
        );
    }

    private Map<HomeSectionKey, HomeSectionEntity> homeSections() {
        Map<HomeSectionKey, HomeSectionEntity> sections = new EnumMap<>(HomeSectionKey.class);
        homeSectionRepository.findAllByOrderBySortOrderAsc()
                .forEach(section -> sections.put(section.getSectionKey(), section));
        if (sections.size() != HomeSectionKey.values().length) {
            throw new IllegalStateException("홈 고정 섹션 구성이 완전하지 않습니다.");
        }
        return sections;
    }

    private Map<String, Object> sectionContent(
            Map<HomeSectionKey, HomeSectionEntity> sections,
            HomeSectionKey key
    ) {
        HomeSectionEntity section = sections.get(key);
        if (section == null) {
            throw new IllegalStateException("필수 홈 섹션을 찾을 수 없습니다: " + key);
        }
        return section.getContent();
    }

    private AnnouncementResponse announcement(Map<String, Object> content) {
        return new AnnouncementResponse(
                stringSetting(content, "announcementText"),
                new LinkResponse(
                        linkTypeSetting(content, "linkType"),
                        stringSetting(content, "linkValue")
                )
        );
    }

    private ServiceGuideResponse serviceGuide(Map<String, Object> content) {
        Object rawLinks = content.get("links");
        if (!(rawLinks instanceof List<?> links)) {
            throw new IllegalStateException("서비스 안내 링크 설정이 올바르지 않습니다.");
        }
        return new ServiceGuideResponse(
                numberSetting(content, "shippingFee"),
                numberSetting(content, "freeShippingThreshold"),
                links.stream().map(String::valueOf).toList()
        );
    }

    private String stringSetting(Map<String, Object> content, String key) {
        Object value = content.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("홈 섹션 문자열 설정이 올바르지 않습니다: " + key);
        }
        return text;
    }

    private long numberSetting(Map<String, Object> content, String key) {
        Object value = content.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("홈 섹션 숫자 설정이 올바르지 않습니다: " + key);
        }
        return number.longValue();
    }

    private MerchandisingLinkType linkTypeSetting(Map<String, Object> content, String key) {
        return MerchandisingLinkType.valueOf(stringSetting(content, key));
    }

    private CollectionCardResponse toCollectionCard(CollectionEntity collection) {
        return new CollectionCardResponse(
                collection.getId(),
                collection.getSlug(),
                collection.getTitle(),
                collection.getDescription(),
                media(collection.getHeroStorageKey(), collection.getHeroAltText()),
                collection.getSortOrder(),
                collection.getPublishedAt()
        );
    }

    private HeroSlideResponse toHeroSlide(HomeHeroSlideEntity slide) {
        return new HeroSlideResponse(
                slide.getId(),
                slide.getTitle(),
                slide.getDescription(),
                media(slide.getImageStorageKey(), slide.getImageAltText()),
                new LinkResponse(slide.getLinkType(), slide.getLinkValue()),
                slide.getSortOrder()
        );
    }

    private LifestyleContentResponse toLifestyleContent(HomeLifestyleContentEntity content) {
        return new LifestyleContentResponse(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                media(content.getImageStorageKey(), content.getImageAltText()),
                new LinkResponse(content.getLinkType(), content.getLinkValue()),
                content.getSortOrder()
        );
    }

    private MediaResponse media(String storageKey, String alt) {
        return new MediaResponse("/media/" + storageKey, alt);
    }
}
