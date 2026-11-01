package interview.project.github.api;

import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import interview.project.github.exception.UpstreamTimeoutException;
import interview.project.github.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Maps every error to the body required by the specification: {@code {"status": <code>, "Message": "<reason>"}}.
 * The content type is always set explicitly, so the body is written even when the client accepts only XML.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return error(HttpStatus.NOT_ACCEPTABLE,
                "Requested media type is not supported, this endpoint produces only application/json");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Other Spring MVC exceptions (e.g. 405 Method Not Allowed) keep their status and headers.
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return error(status, detail != null ? detail : ex.getMessage(), errorResponse.getHeaders());
        }
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
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

    private static long roundUpToSeconds(Duration duration) {
        return duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
    }
}
