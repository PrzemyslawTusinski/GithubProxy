package interview.project.github.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Settings of the GitHub API client, bound from {@code github.*} properties.
 *
 * @param api            GitHub API location ({@code github.api.base-url})
 * @param token          optional personal access token; sent as {@code Authorization: Bearer} when not blank
 * @param connectTimeout TCP connect timeout
 * @param readTimeout    maximum time to wait for a response from GitHub
 * @param pageSize       {@code per_page} requested from GitHub (GitHub allows at most 100)
 * @param maxConcurrency maximum number of branch requests sent to GitHub at the same time
 */
@Validated
@ConfigurationProperties(prefix = "github")
public record GithubProperties(
        @Valid @NotNull Api api,
        String token,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) @Max(100) int pageSize,
        @Min(1) int maxConcurrency) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public record Api(@NotNull URI baseUrl) {
    }
}
