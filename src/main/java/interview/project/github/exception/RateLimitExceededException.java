package interview.project.github.exception;

import java.time.Duration;
import java.util.Optional;

public class RateLimitExceededException extends GithubApiException {

    private final Duration retryAfter;

    /**
     * @param retryAfter how long the caller should wait before retrying, or {@code null} when GitHub did not say
     */
    public RateLimitExceededException(Duration retryAfter) {
        super("GitHub API rate limit exceeded. Try again later or configure a GitHub token (GITHUB_TOKEN)");
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
