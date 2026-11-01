package interview.project.github.api;

import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import interview.project.github.exception.UpstreamTimeoutException;
import interview.project.github.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * The content type is always set explicitly, so the body is written even when the client accepts only XML.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        ex.getRetryAfter().ifPresent(retryAfter ->
                headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(roundUpToSeconds(retryAfter))));
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), headers);
    }

    @ExceptionHandler(UpstreamErrorException.class)
    public ResponseEntity<ApiError> handleUpstreamError(UpstreamErrorException ex) {
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(UpstreamTimeoutException.class)
    public ResponseEntity<ApiError> handleUpstreamTimeout(UpstreamTimeoutException ex) {
        return error(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return createResponseEntity(ProblemDetail.forStatusAndDetail(status, message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String detail = body instanceof ProblemDetail problemDetail && problemDetail.getDetail() != null
                ? problemDetail.getDetail()
                : reasonPhrase(statusCode);
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(statusCode.value(), detail));
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, String message) {
        return error(status, message, HttpHeaders.EMPTY);
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, String message, HttpHeaders headers) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(status.value(), message));
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : String.valueOf(statusCode.value());
    }

    private static long roundUpToSeconds(Duration duration) {
        return duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
    }
}
