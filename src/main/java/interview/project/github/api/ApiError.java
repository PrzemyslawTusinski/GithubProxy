package interview.project.github.api;

import com.fasterxml.jackson.annotation.JsonProperty;

// The task requires the capitalised "Message".
public record ApiError(int status, @JsonProperty("Message") String message) {
}
