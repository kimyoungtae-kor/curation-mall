package kr.co.petcuration.payment.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.co.petcuration.order.application.CommerceActorResolver;
import kr.co.petcuration.payment.api.PaymentApiModels.ConfirmRequest;
import kr.co.petcuration.payment.api.PaymentApiModels.ConfirmResult;
import kr.co.petcuration.payment.api.PaymentApiModels.Envelope;
import kr.co.petcuration.payment.application.PaymentService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CommerceActorResolver actorResolver;

    public PaymentController(PaymentService paymentService, CommerceActorResolver actorResolver) {
        this.paymentService = paymentService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/confirm")
    ResponseEntity<Envelope<ConfirmResult>> confirm(
            @Valid @RequestBody ConfirmRequest body,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            HttpServletRequest request
    ) {
        ConfirmResult result = paymentService.confirm(body, idempotencyKey, actorResolver.resolveOptional(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Envelope<>(result));
    }
}
