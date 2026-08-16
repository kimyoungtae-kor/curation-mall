package kr.co.petcuration.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderApiModels {

    private OrderApiModels() {
    }

    public record QuoteRequest(
            @NotBlank String orderType,
            @NotEmpty @Size(max = 100) List<@NotNull UUID> cartItemIds
    ) {
    }

    public record CreateOrderRequest(
            @NotBlank String orderType,
            @NotEmpty @Size(max = 100) List<@NotNull UUID> cartItemIds,
            @Valid @NotNull Buyer buyer,
            @Valid @NotNull Shipping shipping,
            @Valid @NotNull Agreements agreements
    ) {
    }

    public record Buyer(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^[0-9+ -]{8,30}$") String phone
    ) {
    }

    public record Shipping(
            @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Pattern(regexp = "^[0-9+ -]{8,30}$") String recipientPhone,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Size(max = 255) String address1,
            @Size(max = 255) String address2,
            @Size(max = 500) String deliveryMessage
    ) {
    }

    public record Agreements(
            @AssertTrue boolean purchaseTermsAccepted,
            @AssertTrue boolean privacyCollectionAccepted
    ) {
    }

    public record GuestLookupRequest(
            @NotBlank @Size(max = 40) String orderNumber,
            @NotBlank @Size(max = 200) String guestLookupToken
    ) {
    }

    public record Envelope<T>(T data) {
    }

    public record Quote(
            String orderType,
            List<QuoteLine> lines,
            long itemsAmount,
            long discountAmount,
            long shippingAmount,
            long totalAmount,
            String currency,
            List<String> warnings,
            Instant quotedAt
    ) {
    }

    public record QuoteLine(
            UUID cartItemId,
            UUID variantId,
            String productName,
            String optionLabel,
            int quantity,
            long unitPrice,
            long lineAmount,
            String availability
    ) {
    }

    public record CreateOrderResult(
            boolean replayed,
            OrderSummary order,
            PaymentAttempt payment,
            String guestLookupToken
    ) {
    }

    public record OrderSummary(
            String orderNumber,
            String orderType,
            String orderStatus,
            String paymentStatus,
            long itemsAmount,
            long discountAmount,
            long shippingAmount,
            long totalAmount,
            String currency,
            Instant reservationExpiresAt,
            Instant createdAt,
            int itemCount,
            String representativeItemName
    ) {
    }

    public record PaymentAttempt(
            UUID paymentAttemptId,
            String provider,
            String method,
            long amount,
            String status,
            Instant approvedAt,
            boolean testPayment
    ) {
    }

    public record OrderDetail(
            String orderNumber,
            String orderType,
            String orderStatus,
            String paymentStatus,
            Buyer buyer,
            Shipping shipping,
            List<OrderItem> items,
            long itemsAmount,
            long discountAmount,
            long shippingAmount,
            long totalAmount,
            String currency,
            List<PaymentAttempt> payments,
            List<StatusHistory> statusHistory,
            Instant orderedAt,
            Instant paidAt
    ) {
    }

    public record OrderItem(
            String productName,
            String brandName,
            String sku,
            String optionLabel,
            long unitPrice,
            int quantity,
            long lineAmount,
            String imageUrl
    ) {
    }

    public record StatusHistory(
            String fromStatus,
            String toStatus,
            String reason,
            Instant createdAt
    ) {
    }

    public record OrderListResponse(List<OrderSummary> data, PageMetadata page) {
    }

    public record PageMetadata(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }
}
