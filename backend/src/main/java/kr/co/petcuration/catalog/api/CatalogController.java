package kr.co.petcuration.catalog.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductDetailResponse;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductDetailEnvelope;
import kr.co.petcuration.catalog.api.CatalogResponses.ProductListResponse;
import kr.co.petcuration.catalog.application.CatalogQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/catalog/products")
public class CatalogController {

    private static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";
    private static final String PAGE_PATTERN = "(?:0|[1-9][0-9]{0,8})";
    private static final String SIZE_PATTERN = "(?:[1-9]|[1-9][0-9]|100)";
    private static final String SORT_PATTERN = "(?:newest,desc|price,asc|price,desc|name,asc)";

    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping
    ProductListResponse products(
            @RequestParam(required = false)
            @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
            @Pattern(regexp = "[^\\p{Cntrl}]*", message = "검색어에 제어 문자를 사용할 수 없습니다.")
            String q,
            @RequestParam(required = false)
            @Size(max = 120, message = "브랜드 슬러그는 120자 이하여야 합니다.")
            @Pattern(regexp = SLUG_PATTERN, message = "올바른 브랜드 슬러그가 아닙니다.")
            String brand,
            @RequestParam(required = false)
            @Size(max = 120, message = "카테고리 슬러그는 120자 이하여야 합니다.")
            @Pattern(regexp = SLUG_PATTERN, message = "올바른 카테고리 슬러그가 아닙니다.")
            String category,
            @RequestParam(required = false)
            @Size(max = 50, message = "동물 종 슬러그는 50자 이하여야 합니다.")
            @Pattern(regexp = SLUG_PATTERN, message = "올바른 동물 종 슬러그가 아닙니다.")
            String species,
            @RequestParam(defaultValue = "false")
            @Pattern(regexp = "(?:true|false)", message = "inStock은 true 또는 false여야 합니다.")
            String inStock,
            @RequestParam(defaultValue = "0")
            @Pattern(regexp = PAGE_PATTERN, message = "page는 0 이상의 정수여야 합니다.")
            String page,
            @RequestParam(defaultValue = "20")
            @Pattern(regexp = SIZE_PATTERN, message = "size는 1 이상 100 이하의 정수여야 합니다.")
            String size,
            @RequestParam(defaultValue = "newest,desc")
            @Pattern(regexp = SORT_PATTERN, message = "지원하지 않는 상품 정렬 방식입니다.")
            String sort
    ) {
        return catalogQueryService.findProducts(
                q,
                brand,
                category,
                species,
                Boolean.parseBoolean(inStock),
                Integer.parseInt(page),
                Integer.parseInt(size),
                sort
        );
    }

    @GetMapping("/{slug}")
    ProductDetailEnvelope product(
            @PathVariable
            @Pattern(regexp = SLUG_PATTERN, message = "올바른 상품 슬러그가 아닙니다.")
            String slug
    ) {
        return new ProductDetailEnvelope(catalogQueryService.findProduct(slug));
    }
}
