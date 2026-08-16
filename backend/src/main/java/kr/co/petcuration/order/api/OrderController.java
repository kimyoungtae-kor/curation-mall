package kr.co.petcuration.order.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import kr.co.petcuration.order.api.OrderApiModels.CreateOrderRequest;
import kr.co.petcuration.order.api.OrderApiModels.CreateOrderResult;
import kr.co.petcuration.order.api.OrderApiModels.Envelope;
import kr.co.petcuration.order.api.OrderApiModels.GuestLookupRequest;
import kr.co.petcuration.order.api.OrderApiModels.OrderDetail;
import kr.co.petcuration.order.api.OrderApiModels.OrderListResponse;
import kr.co.petcuration.order.api.OrderApiModels.Quote;
import kr.co.petcuration.order.api.OrderApiModels.QuoteRequest;
import kr.co.petcuration.order.application.CommerceActorResolver;
import kr.co.petcuration.order.application.OrderService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final CommerceActorResolver actorResolver;

    public OrderController(OrderService orderService, CommerceActorResolver actorResolver) {
        this.orderService = orderService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/orders/quote")
    ResponseEntity<Envelope<Quote>> quote(
            @Valid @RequestBody QuoteRequest body,
            HttpServletRequest request
    ) {
        Quote quote = orderService.quote(body.orderType(), body.cartItemIds(), actorResolver.resolve(request));
        return noStore(new Envelope<>(quote));
    }

    @PostMapping("/orders")
    ResponseEntity<Envelope<CreateOrderResult>> create(
            @Valid @RequestBody CreateOrderRequest body,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            HttpServletRequest request
    ) {
        CreateOrderResult result = orderService.create(body, idempotencyKey, actorResolver.resolve(request));
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore());
        if (result.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(new Envelope<>(result));
    }

    @GetMapping("/orders")
    ResponseEntity<OrderListResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orderService.listMember(actorResolver.resolve(request), page, size));
    }

    @GetMapping("/orders/{orderNumber}")
    ResponseEntity<Envelope<OrderDetail>> detail(
            @PathVariable String orderNumber,
            HttpServletRequest request
    ) {
        return noStore(new Envelope<>(orderService.memberDetail(orderNumber, actorResolver.resolve(request))));
    }

    @PostMapping("/guest-orders/lookup")
    ResponseEntity<Envelope<OrderDetail>> guestLookup(@Valid @RequestBody GuestLookupRequest body) {
        return noStore(new Envelope<>(orderService.guestDetail(body.orderNumber(), body.guestLookupToken())));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
