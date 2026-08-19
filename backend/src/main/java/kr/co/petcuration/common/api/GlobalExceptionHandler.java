package kr.co.petcuration.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://pet-curation-mall.example/problems/";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(
                exception.getStatus(),
                exception.getCode(),
                exception.getTitle(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "요청한 자원을 찾을 수 없습니다.",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldViolation> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toViolation)
                .toList();

        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "입력값을 확인해 주세요.",
                "하나 이상의 입력값이 유효하지 않습니다.",
                request,
                errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintValidation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldViolation> errors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiErrorResponse.FieldViolation(
                        lastPathSegment(violation.getPropertyPath().toString()),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()
                ))
                .toList();

        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "입력값을 확인해 주세요.",
                "하나 이상의 입력값이 유효하지 않습니다.",
                request,
                errors
        );
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "요청 형식이 올바르지 않습니다.",
                "파라미터와 요청 본문을 확인해 주세요.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        boolean idempotency = "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName());
        return response(
                HttpStatus.BAD_REQUEST,
                idempotency ? "IDEMPOTENCY_KEY_REQUIRED" : "INVALID_REQUEST",
                idempotency ? "멱등성 키가 필요합니다." : "필수 요청 헤더가 없습니다.",
                idempotency
                        ? "주문 생성과 결제 확인 요청에 UUID 형식 Idempotency-Key 헤더를 보내 주세요."
                        : exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "IMAGE_FILE_TOO_LARGE",
                "이미지 파일이 너무 큽니다.",
                "이미지는 한 장당 최대 8MB, 요청당 최대 10MB까지 업로드할 수 있습니다.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "IMAGE_REQUIRED",
                "이미지 파일이 필요합니다.",
                "multipart/form-data의 file 항목에 이미지를 첨부해 주세요.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure: method={}, uri={}", request.getMethod(), request.getRequestURI(), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "요청을 처리하지 못했습니다.",
                "잠시 후 다시 시도해 주세요.",
                request,
                List.of()
        );
    }

    private ApiErrorResponse.FieldViolation toViolation(FieldError error) {
        return new ApiErrorResponse.FieldViolation(
                error.getField(),
                error.getCode() == null ? "Invalid" : error.getCode(),
                error.getDefaultMessage() == null ? "유효하지 않은 값입니다." : error.getDefaultMessage()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> errors
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                TYPE_PREFIX + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                code,
                detail,
                request.getRequestURI(),
                request.getHeader("X-Request-Id") == null
                        ? UUID.randomUUID().toString()
                        : request.getHeader("X-Request-Id"),
                Instant.now(),
                errors
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String lastPathSegment(String path) {
        int separator = path.lastIndexOf('.');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
