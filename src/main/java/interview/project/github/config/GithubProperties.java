package interview.project.github.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Settings of the GitHub API client, bound from {@code github.*} properties (see the README).
 *
 * @param requestTimeout upper bound for a whole listing, all pages of repositories and branches together
 */
@Validated
@ConfigurationProperties(prefix = "github")
public record GithubProperties(
        @NotNull URI baseUrl,
        String token,
        @Min(1) @Max(100) int pageSize,
        @Min(1) int maxConcurrency,
        @NotNull Duration requestTimeout) {

    public boolean hasToken() {
        return StringUtils.hasText(token);
    }
}
