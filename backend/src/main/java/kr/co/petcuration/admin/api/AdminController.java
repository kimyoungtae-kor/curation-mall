package kr.co.petcuration.admin.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.admin.api.AdminApiModels.AdminOrderDetail;
import kr.co.petcuration.admin.api.AdminApiModels.AdminOrderSummary;
import kr.co.petcuration.admin.api.AdminApiModels.Envelope;
import kr.co.petcuration.admin.api.AdminApiModels.HomeSection;
import kr.co.petcuration.admin.api.AdminApiModels.HomeSectionUpdate;
import kr.co.petcuration.admin.api.AdminApiModels.HeroSlide;
import kr.co.petcuration.admin.api.AdminApiModels.HeroSlideUpdate;
import kr.co.petcuration.admin.api.AdminApiModels.PageResponse;
import kr.co.petcuration.admin.api.AdminApiModels.ProductDetail;
import kr.co.petcuration.admin.api.AdminApiModels.ProductSummary;
import kr.co.petcuration.admin.api.AdminApiModels.ProductUpsertRequest;
import kr.co.petcuration.admin.api.AdminApiModels.ReferenceItem;
import kr.co.petcuration.admin.api.AdminApiModels.StatusRequest;
import kr.co.petcuration.admin.api.AdminApiModels.StockRequest;
import kr.co.petcuration.admin.api.AdminApiModels.TransitionRequest;
import kr.co.petcuration.admin.api.AdminApiModels.UserSummary;
import kr.co.petcuration.admin.api.AdminApiModels.Variant;
import kr.co.petcuration.admin.application.AdminService;
import kr.co.petcuration.order.application.CommerceActorResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final CommerceActorResolver actorResolver;

    public AdminController(AdminService adminService, CommerceActorResolver actorResolver) {
        this.adminService = adminService;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/products")
    ResponseEntity<PageResponse<ProductSummary>> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return noStore(adminService.products(q, status, page, size));
    }

    @PostMapping("/products")
    ResponseEntity<Envelope<ProductDetail>> createProduct(
            @Valid @RequestBody ProductUpsertRequest body,
            HttpServletRequest request
    ) {
        ProductDetail product = adminService.createProduct(body, actorResolver.resolve(request).userId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new Envelope<>(product));
    }

    @GetMapping("/products/{productId}")
    ResponseEntity<Envelope<ProductDetail>> product(@PathVariable UUID productId) {
        return noStore(new Envelope<>(adminService.product(productId)));
    }

    @PutMapping("/products/{productId}")
    ResponseEntity<Envelope<ProductDetail>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductUpsertRequest body,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(adminService.updateProduct(productId, body,
                actorResolver.resolve(request).userId())));
    }

    @DeleteMapping("/products/{productId}")
    ResponseEntity<Void> deleteProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) @Min(0) Long version,
            @RequestParam(defaultValue = "false") boolean confirmOrderHistory,
            HttpServletRequest request
    ) {
        adminService.deleteProduct(productId, version, confirmOrderHistory,
                actorResolver.resolve(request).userId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PatchMapping("/products/{productId}/status")
    ResponseEntity<Envelope<ProductDetail>> productStatus(
            @PathVariable UUID productId,
            @Valid @RequestBody StatusRequest body,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(adminService.changeProductStatus(productId, body.status(), body.version(),
                actorResolver.resolve(request).userId())));
    }

    @PatchMapping("/variants/{variantId}/stock")
    ResponseEntity<Envelope<Variant>> stock(
            @PathVariable UUID variantId,
            @Valid @RequestBody StockRequest body,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(adminService.changeStock(variantId, body,
                actorResolver.resolve(request).userId())));
    }

    @GetMapping("/{type:brands|categories|species}")
    ResponseEntity<Envelope<List<ReferenceItem>>> references(@PathVariable String type) {
        return noStore(new Envelope<>(adminService.references(type)));
    }

    @GetMapping("/home-sections")
    ResponseEntity<Envelope<List<HomeSection>>> homeSections() {
        return noStore(new Envelope<>(adminService.homeSections()));
    }

    @PutMapping("/home-sections/{sectionId}")
    ResponseEntity<Envelope<HomeSection>> updateHomeSection(
            @PathVariable UUID sectionId,
            @Valid @RequestBody HomeSectionUpdate body,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(adminService.updateHomeSection(sectionId, body,
                actorResolver.resolve(request).userId())));
    }

    @GetMapping("/hero-slides")
    ResponseEntity<Envelope<List<HeroSlide>>> heroSlides() {
        return noStore(new Envelope<>(adminService.heroSlides()));
    }

    @PutMapping("/hero-slides/{slideId}")
    ResponseEntity<Envelope<HeroSlide>> updateHeroSlide(
            @PathVariable UUID slideId,
            @Valid @RequestBody HeroSlideUpdate body,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(adminService.updateHeroSlide(slideId, body,
                actorResolver.resolve(request).userId())));
    }

    @GetMapping("/orders")
    ResponseEntity<PageResponse<AdminOrderSummary>> orders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return noStore(adminService.orders(q, status, page, size));
    }

    @GetMapping("/orders/{orderNumber}")
    ResponseEntity<Envelope<AdminOrderDetail>> order(@PathVariable String orderNumber) {
        return noStore(new Envelope<>(adminService.order(orderNumber)));
    }

    @PostMapping("/orders/{orderNumber}/transitions")
    ResponseEntity<Envelope<AdminOrderDetail>> transition(
            @PathVariable String orderNumber,
            @Valid @RequestBody TransitionRequest body,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(adminService.transition(orderNumber, body,
                actorResolver.resolve(request).userId())));
    }

    @GetMapping("/users")
    ResponseEntity<PageResponse<UserSummary>> users(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return noStore(adminService.users(q, page, size));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
