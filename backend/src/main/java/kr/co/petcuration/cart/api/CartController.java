package kr.co.petcuration.cart.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kr.co.petcuration.cart.api.CartResponses.CartEnvelope;
import kr.co.petcuration.cart.application.CartService;
import kr.co.petcuration.identity.application.CurrentActorResolver;
import kr.co.petcuration.identity.application.OwnerIdentity;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final CurrentActorResolver actorResolver;

    public CartController(CartService cartService, CurrentActorResolver actorResolver) {
        this.cartService = cartService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    ResponseEntity<CartEnvelope> cart(HttpServletRequest request, HttpServletResponse response) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        return noStore(cartService.getCart(owner));
    }

    @PostMapping("/items")
    ResponseEntity<CartEnvelope> add(
            @Valid @RequestBody AddCartItemRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        return noStore(cartService.addItem(owner, body.variantId(), body.quantity()));
    }

    @PatchMapping("/items/{itemId}")
    ResponseEntity<CartEnvelope> update(
            @PathVariable UUID itemId,
            @Valid @RequestBody ChangeQuantityRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        return noStore(cartService.updateItem(owner, itemId, body.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    ResponseEntity<CartEnvelope> delete(
            @PathVariable UUID itemId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = actorResolver.requireOwner(request, response);
        return noStore(cartService.deleteItem(owner, itemId));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record AddCartItemRequest(
            @NotNull UUID variantId,
            @Min(1) @Max(10) int quantity
    ) {
    }

    record ChangeQuantityRequest(@Min(1) @Max(10) int quantity) {
    }
}
