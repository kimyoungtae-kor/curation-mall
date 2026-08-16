package kr.co.petcuration.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        String traceId,
        Instant timestamp,
        List<FieldViolation> fieldErrors
) {
    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public record FieldViolation(String field, String code, String message) {
    }
}
