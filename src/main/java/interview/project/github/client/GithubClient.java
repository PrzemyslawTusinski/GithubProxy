package interview.project.github.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GithubClient {

    private static final Logger log = LoggerFactory.getLogger(GithubClient.class);

    // GitHub's maximum; with its default of 30 a listing would use about three times more of the rate limit.
    private static final int PAGE_SIZE = 100;

    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private final WebClient webClient;
    private final URI baseUrl;

    // The connector (timeouts, gzip) comes from Boot's auto-configuration and the spring.http.reactiveclient.*
    // properties through the injected builder; only the GitHub-specific headers are added here.
    public GithubClient(WebClient.Builder webClientBuilder, GithubProperties properties) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "github-proxy")
                .defaultHeaders(headers -> {
                    if (StringUtils.hasText(properties.token())) {
                        headers.setBearerAuth(properties.token());
                    }
                })
                .build();
        this.baseUrl = properties.baseUrl();
    }

    public Flux<GithubRepository> listUserRepositories(String username) {
        URI firstPage = firstPageUri("/users/{username}/repos", username);
        return fetchAllPages(firstPage, GithubRepository[].class, () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "GitHub user '%s' not found".formatted(username)));
    }

    public Flux<GithubBranch> listBranches(String owner, String repository) {
        URI firstPage = firstPageUri("/repos/{owner}/{repository}/branches", owner, repository);
        return fetchAllPages(firstPage, GithubBranch[].class, null);
    }

    private URI firstPageUri(String pathTemplate, Object... pathVariables) {
        return UriComponentsBuilder.fromUri(baseUrl)
                .path(pathTemplate)
                .queryParam("per_page", PAGE_SIZE)
                .encode()
                .buildAndExpand(pathVariables)
                .toUri();
    }

    private <T> Flux<T> fetchAllPages(URI firstPage, Class<T[]> pageType,
                                      Supplier<ResponseStatusException> notFoundError) {
        return fetchPage(firstPage, pageType, notFoundError)
                .expand(page -> Mono.justOrEmpty(nextPageLink(page.getHeaders()))
                        .flatMap(next -> fetchPage(next, pageType, notFoundError)))
                .concatMapIterable(page -> Arrays.asList(page.getBody()))
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error -> {
                    log.warn("GitHub API request failed: GET {}: {}", firstPage, error.toString());
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Could not get a valid response from the GitHub API", error);
                });
    }

    private <T> Mono<ResponseEntity<T[]>> fetchPage(URI uri, Class<T[]> pageType,
                                                    Supplier<ResponseStatusException> notFoundError) {
        log.debug("GitHub request: GET {}", uri);
        return webClient.get()
                .uri(uri)
                .retrieve()
                // Redirects are not followed, so a 3xx is an error like a 4xx or 5xx.
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.createException().map(ex -> toStatusException(uri, ex, notFoundError)))
                // A whole-page decode, unlike an element-by-element one, tells an empty body apart from an empty array.
                .toEntity(pageType)
                .map(page -> {
                    if (page.getBody() == null) {
                        log.warn("GitHub API returned an empty body for GET {}", uri);
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "GitHub API returned an empty response");
                    }
                    return page;
                });
    }

    private ResponseStatusException toStatusException(URI uri, WebClientResponseException ex,
                                                      Supplier<ResponseStatusException> notFoundError) {
        HttpStatusCode status = ex.getStatusCode();
        if (isRateLimited(status, ex.getHeaders())) {
            log.warn("GitHub API rate limit exceeded: GET {} returned {}", uri, status.value());
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub API rate limit exceeded. Try again later or configure a GitHub token (GITHUB_TOKEN)");
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND) && notFoundError != null) {
            log.info("GitHub API returned 404 for GET {}", uri);
            return notFoundError.get();
        }
        log.warn("GitHub API error: GET {} returned {} {}", uri, status.value(),
                LogFormatUtils.formatValue(ex.getResponseBodyAsString(), 300, true));
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "GitHub API responded with status " + status.value());
    }

    // GitHub answers 403 instead of 429 when the primary rate limit is used up.
    private static boolean isRateLimited(HttpStatusCode status, HttpHeaders headers) {
        return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                || (status.isSameCodeAs(HttpStatus.FORBIDDEN) && "0".equals(headers.getFirst("X-RateLimit-Remaining")));
    }

    /**
     * The token is sent with every request, so only the path and query of a pagination link are used and the request
     * always goes to the configured GitHub host.
     */
    private URI nextPageLink(HttpHeaders headers) {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (link == null) {
            return null;
        }
        Matcher next = NEXT_LINK.matcher(link);
        if (!next.find()) {
            return null;
        }
        URI uri = URI.create(next.group(1));
        return UriComponentsBuilder.fromUri(baseUrl)
                .replacePath(uri.getRawPath())
                .replaceQuery(uri.getRawQuery())
                .build(true)
                .toUri();
    }
}
