package kr.co.petcuration.cart.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import kr.co.petcuration.cart.api.CartResponses.WishlistListResponse;
import kr.co.petcuration.cart.api.CartResponses.WishlistMutationEnvelope;
import kr.co.petcuration.cart.application.WishlistService;
import kr.co.petcuration.identity.application.CurrentActorResolver;
import kr.co.petcuration.identity.application.OwnerIdentity;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CurrentActorResolver actorResolver;

    public WishlistController(WishlistService wishlistService, CurrentActorResolver actorResolver) {
        this.wishlistService = wishlistService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    ResponseEntity<WishlistListResponse> wishlist(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(wishlistService.getWishlist(owner, page, size));
    }

    @PostMapping("/{productId}")
    ResponseEntity<WishlistMutationEnvelope> add(
            @PathVariable UUID productId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(wishlistService.add(owner, productId));
    }

    @DeleteMapping("/{productId}")
    ResponseEntity<Void> delete(
            @PathVariable UUID productId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        wishlistService.delete(owner, productId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
