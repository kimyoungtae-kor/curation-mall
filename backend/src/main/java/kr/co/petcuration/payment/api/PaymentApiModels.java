package kr.co.petcuration.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PaymentApiModels {

    private PaymentApiModels() {
    }

    public record ConfirmRequest(
            @NotBlank String provider,
            @NotBlank @Size(max = 40) String orderNumber,
            @Size(max = 200) String paymentKey,
            String simulationResult,
            @PositiveOrZero long amount,
            @Size(max = 200) String guestLookupToken
    ) {
    }

    public record Envelope<T>(T data) {
    }

    public record ConfirmResult(
            String orderNumber,
            String orderStatus,
            PaymentResult payment,
            String nextAction
    ) {
    }

    public record PaymentResult(
            UUID paymentAttemptId,
            String provider,
            String method,
            String status,
            long amount,
            Instant approvedAt,
            boolean testPayment,
            String failureCode,
            String failureMessage
    ) {
    }
}
