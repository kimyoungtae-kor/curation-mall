package kr.co.petcuration.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.order.api.OrderApiModels;

public final class AdminApiModels {

    private AdminApiModels() {
    }

    public record Envelope<T>(T data) {
    }

    public record PageResponse<T>(List<T> data, OrderApiModels.PageMetadata page) {
    }

    public record ProductSummary(
            UUID id,
            String slug,
            String name,
            String brandName,
            String status,
            long minimumPrice,
            int totalStock,
            long version,
            Instant updatedAt
    ) {
    }

    public record ProductDetail(
            UUID id,
            UUID brandId,
            String slug,
            String name,
            String summary,
            String description,
            String status,
            boolean featured,
            List<UUID> categoryIds,
            List<UUID> speciesIds,
            List<Variant> variants,
            List<Image> images,
            long version
    ) {
    }

    public record ProductUpsertRequest(
            @NotNull UUID brandId,
            @NotBlank @Size(max = 160) String slug,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String summary,
            String description,
            @NotBlank String status,
            boolean featured,
            List<UUID> categoryIds,
            List<UUID> speciesIds,
            @Valid @NotEmpty List<VariantInput> variants,
            @Valid List<ImageInput> images,
            Long version
    ) {
    }

    public record VariantInput(
            UUID id,
            Long version,
            @NotBlank @Size(max = 100) String sku,
            @NotBlank @Size(max = 150) String optionLabel,
            @PositiveOrZero long price,
            @PositiveOrZero int stockQuantity,
            @NotBlank String status,
            @Min(0) int sortOrder
    ) {
    }

    public record ImageInput(
            UUID id,
            @NotBlank @Size(max = 500) String storageKey,
            @NotBlank @Size(max = 300) String alt,
            @Min(0) int sortOrder
    ) {
    }

    public record Variant(
            UUID id,
            String sku,
            String optionLabel,
            long price,
            int stockQuantity,
            String status,
            int sortOrder,
            long version
    ) {
    }

    public record Image(UUID id, String storageKey, String alt, int sortOrder) {
    }

    public record StatusRequest(@NotBlank String status, @NotNull Long version) {
    }

    public record StockRequest(@PositiveOrZero int stockQuantity, @NotNull Long version) {
    }

    public record HomeSection(
            UUID id,
            String sectionKey,
            String title,
            String content,
            int sortOrder,
            long version,
            Instant updatedAt
    ) {
    }

    public record HomeSectionUpdate(
            @Size(max = 200) String title,
            @NotBlank String content,
            @Min(1) int sortOrder,
            @NotNull Long version
    ) {
    }

    public record HeroSlide(
            UUID id,
            String title,
            String description,
            String imageStorageKey,
            String imageAlt,
            String linkType,
            String linkValue,
            String status,
            int sortOrder,
            long version
    ) {
    }

    public record HeroSlideUpdate(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 500) String imageStorageKey,
            @NotBlank @Size(max = 300) String imageAlt,
            @NotBlank String linkType,
            @NotBlank @Size(max = 200) String linkValue,
            @NotBlank String status,
            @Min(1) int sortOrder,
            @NotNull Long version
    ) {
    }

    public record AdminOrderSummary(
            String orderNumber,
            String orderType,
            String buyerName,
            String orderStatus,
            String paymentStatus,
            long totalAmount,
            Instant orderedAt
    ) {
    }

    public record AdminOrderDetail(OrderApiModels.OrderDetail order, long version) {
    }

    public record TransitionRequest(
            @NotBlank String toStatus,
            @NotBlank @Size(max = 500) String reason,
            @NotNull Long version
    ) {
    }

    public record UserSummary(
            UUID id,
            String email,
            String name,
            String phone,
            String status,
            long orderCount,
            long totalPurchased,
            Instant createdAt
    ) {
    }

    public record ReferenceItem(UUID id, String code, String name) {
    }
}
