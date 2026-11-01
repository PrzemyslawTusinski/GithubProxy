package interview.project.github.api;

import interview.project.github.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleUnexpected_unknownException_returns500WithGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("internal details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(new ApiError(500, "Internal server error"));
    }

    @Test
    void handleException_methodNotSupported_returns405WithAllowHeaderAndErrorBody() throws Exception {
        var exception = new HttpRequestMethodNotSupportedException("POST", List.of("GET"));

        ResponseEntity<Object> response =
                handler.handleException(exception, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isEqualTo(Set.of(HttpMethod.GET));
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(new ApiError(405, "Method 'POST' is not supported."));
    }

    @Test
    void handleRateLimitExceeded_retryAfterWithFractionOfSecond_roundsRetryAfterUp() {
        var exception = new RateLimitExceededException(Duration.ofMillis(1200));

        ResponseEntity<ApiError> response = handler.handleRateLimitExceeded(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(response.getBody()).isEqualTo(new ApiError(503, exception.getMessage()));
    }
}
