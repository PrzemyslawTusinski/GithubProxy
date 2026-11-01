package interview.project.github.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body required by the specification: {@code {"status": <code>, "Message": "<reason>"}}.
 */
public record ApiError(int status, @JsonProperty("Message") String message) {
}
