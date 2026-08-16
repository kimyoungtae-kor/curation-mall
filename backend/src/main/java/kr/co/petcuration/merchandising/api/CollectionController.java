package kr.co.petcuration.merchandising.api;

import jakarta.validation.constraints.Pattern;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.CollectionDetailEnvelope;
import kr.co.petcuration.merchandising.api.MerchandisingResponses.CollectionListResponse;
import kr.co.petcuration.merchandising.application.MerchandisingQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";
    private static final String PAGE_PATTERN = "(?:0|[1-9][0-9]{0,8})";
    private static final String SIZE_PATTERN = "(?:[1-9]|[1-9][0-9]|100)";
    private static final String SORT_PATTERN = "sortOrder,asc";

    private final MerchandisingQueryService merchandisingQueryService;

    public CollectionController(MerchandisingQueryService merchandisingQueryService) {
        this.merchandisingQueryService = merchandisingQueryService;
    }

    @GetMapping
    CollectionListResponse collections(
            @RequestParam(defaultValue = "0")
            @Pattern(regexp = PAGE_PATTERN, message = "page는 0 이상의 정수여야 합니다.")
            String page,
            @RequestParam(defaultValue = "20")
            @Pattern(regexp = SIZE_PATTERN, message = "size는 1 이상 100 이하의 정수여야 합니다.")
            String size,
            @RequestParam(defaultValue = "sortOrder,asc")
            @Pattern(regexp = SORT_PATTERN, message = "지원하지 않는 기획전 정렬 방식입니다.")
            String sort
    ) {
        return merchandisingQueryService.findCollections(Integer.parseInt(page), Integer.parseInt(size));
    }

    @GetMapping("/{slug}")
    CollectionDetailEnvelope collection(
            @PathVariable
            @Pattern(regexp = SLUG_PATTERN, message = "올바른 기획전 슬러그가 아닙니다.")
            String slug
    ) {
        return new CollectionDetailEnvelope(merchandisingQueryService.findCollection(slug));
    }
}
