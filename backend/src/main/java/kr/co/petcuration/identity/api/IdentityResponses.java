package kr.co.petcuration.identity.api;

import java.util.List;
import java.util.UUID;

public final class IdentityResponses {

    private IdentityResponses() {
    }

    public record CsrfEnvelope(CsrfResponse data) {
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record MeEnvelope(MeResponse data) {
    }

    public record MeResponse(
            boolean authenticated,
            UserResponse user,
            int cartCount,
            long wishlistCount
    ) {
    }

    public record AuthenticationEnvelope(AuthenticationResponse data) {
    }

    public record AuthenticationResponse(
            boolean authenticated,
            UserResponse user,
            MergeResultResponse mergeResult
    ) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String name,
            String phone,
            List<String> roles
    ) {
        public UserResponse {
            roles = List.copyOf(roles);
        }
    }

    public record MergeResultEnvelope(MergeResultResponse data) {
    }

    public record MergeResultResponse(
            boolean merged,
            int cartItemCount,
            long wishlistCount,
            List<MergeAdjustmentResponse> adjustments
    ) {
        public MergeResultResponse {
            adjustments = List.copyOf(adjustments);
        }
    }

    public record MergeAdjustmentResponse(
            UUID variantId,
            int beforeMemberQuantity,
            int beforeVisitorQuantity,
            int mergedQuantity,
            String reason
    ) {
    }
}
