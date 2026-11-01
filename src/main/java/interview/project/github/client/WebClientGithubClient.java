package interview.project.github.client;

import interview.project.github.config.GithubProperties;
import interview.project.github.exception.GithubApiException;
import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import interview.project.github.exception.UpstreamTimeoutException;
import interview.project.github.exception.UserNotFoundException;
import io.netty.channel.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link GithubClient} backed by {@link WebClient}. Requests {@code per_page} items per page and follows the
 * {@code Link: <...>; rel="next"} header until the last page.
 */
@Component
public class WebClientGithubClient implements GithubClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientGithubClient.class);

    private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private static final ParameterizedTypeReference<List<GithubRepository>> REPOSITORY_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<GithubBranch>> BRANCH_LIST =
            new ParameterizedTypeReference<>() {
            };

    private static final Pattern LINK = Pattern.compile("<([^>]+)>([^<]*)");
    private static final Pattern REL = Pattern.compile("rel\\s*=\\s*\"?([^\";,]+)\"?");

    private final WebClient webClient;
    private final URI baseUrl;
    private final int pageSize;

    public WebClientGithubClient(WebClient githubWebClient, GithubProperties properties) {
        this.webClient = githubWebClient;
        this.baseUrl = properties.api().baseUrl();
        this.pageSize = properties.pageSize();
    }

    @Override
    public Flux<GithubRepository> listUserRepositories(String username) {
        URI firstPage = firstPageUri("/users/{username}/repos", username);
        return fetchAllPages(firstPage, REPOSITORY_LIST, () -> new UserNotFoundException(username));
    }

    @Override
    public Flux<GithubBranch> listBranches(String owner, String repository) {
        URI firstPage = firstPageUri("/repos/{owner}/{repository}/branches", owner, repository);
        return fetchAllPages(firstPage, BRANCH_LIST, null);
    }

    private URI firstPageUri(String pathTemplate, Object... pathVariables) {
        return UriComponentsBuilder.fromUri(baseUrl)
                .path(pathTemplate)
                .queryParam("per_page", pageSize)
                .encode()
                .buildAndExpand(pathVariables)
                .toUri();
    }

    /**
     * @param notFoundError error to raise on HTTP 404, or {@code null} to treat 404 as an upstream error
     */
    private <T> Flux<T> fetchAllPages(URI firstPage, ParameterizedTypeReference<List<T>> type,
                                      Supplier<GithubApiException> notFoundError) {
        return fetchPage(firstPage, type, notFoundError)
                .expand(page -> page.next() == null ? Mono.empty() : fetchPage(page.next(), type, notFoundError))
                .concatMapIterable(Page::items);
    }

    private <T> Mono<Page<T>> fetchPage(URI uri, ParameterizedTypeReference<List<T>> type,
                                        Supplier<GithubApiException> notFoundError) {
        log.debug("GitHub request: GET {}", uri);
        return webClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> toApiException(uri, response.statusCode(), response.headers().asHttpHeaders(),
                                body, notFoundError)))
                .toEntity(type)
                .map(entity -> toPage(uri, entity))
                .onErrorMap(error -> !(error instanceof GithubApiException), error -> toApiException(uri, error));
    }

    private <T> Page<T> toPage(URI uri, ResponseEntity<List<T>> entity) {
        List<T> items = entity.getBody();
        if (items == null) {
            log.warn("GitHub API returned an empty body for GET {}", uri);
            throw new UpstreamErrorException("GitHub API returned an empty response");
        }
        URI next = nextPageLink(entity.getHeaders().getOrEmpty(HttpHeaders.LINK))
                .map(uri::resolve)
                .map(this::requireSameOriginAsBaseUrl)
                .orElse(null);
        return new Page<>(items, next);
    }

    private GithubApiException toApiException(URI uri, HttpStatusCode status, HttpHeaders headers, String body,
                                              Supplier<GithubApiException> notFoundError) {
        if (isRateLimited(status, headers)) {
            log.warn("GitHub API rate limit exceeded: GET {} returned {}", uri, status.value());
            return new RateLimitExceededException(retryAfter(headers));
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND) && notFoundError != null) {
            log.info("GitHub API returned 404 for GET {}", uri);
            return notFoundError.get();
        }
        log.warn("GitHub API error: GET {} returned {} {}", uri, status.value(),
                LogFormatUtils.formatValue(body, 300, true));
        return new UpstreamErrorException("GitHub API responded with status " + status.value());
    }

    private static GithubApiException toApiException(URI uri, Throwable error) {
        if (isTimeout(error)) {
            log.warn("GitHub API request timed out: GET {}", uri);
            return new UpstreamTimeoutException(error);
        }
        log.warn("GitHub API request failed: GET {}: {}", uri, error.toString());
        return new UpstreamErrorException("Could not get a valid response from the GitHub API", error);
    }

    private static boolean isRateLimited(HttpStatusCode status, HttpHeaders headers) {
        return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                || (status.isSameCodeAs(HttpStatus.FORBIDDEN) && "0".equals(headers.getFirst(RATE_LIMIT_REMAINING)));
    }

    /**
     * GitHub's own {@code Retry-After} wins; otherwise the time left until {@code X-RateLimit-Reset}.
     */
    private static Duration retryAfter(HttpHeaders headers) {
        Long retryAfterSeconds = parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER));
        if (retryAfterSeconds != null) {
            return Duration.ofSeconds(Math.max(0, retryAfterSeconds));
        }
        Long resetEpochSeconds = parseLong(headers.getFirst(RATE_LIMIT_RESET));
        if (resetEpochSeconds != null) {
            Duration untilReset = Duration.between(Instant.now(), Instant.ofEpochSecond(resetEpochSeconds));
            return untilReset.isNegative() ? Duration.ZERO : untilReset;
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException
                    || cause instanceof ConnectTimeoutException
                    || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The token is attached to every request, so pagination links are only followed to the configured GitHub host.
     */
    private URI requireSameOriginAsBaseUrl(URI link) {
        boolean sameOrigin = baseUrl.getScheme().equalsIgnoreCase(link.getScheme())
                && baseUrl.getHost().equalsIgnoreCase(link.getHost())
                && effectivePort(baseUrl) == effectivePort(link);
        if (!sameOrigin) {
            log.warn("GitHub API returned a pagination link to an unexpected host: {}", link);
            throw new UpstreamErrorException("GitHub API returned a pagination link to an unexpected host");
        }
        return link;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Finds the {@code rel="next"} target in GitHub's {@code Link} header(s).
     */
    static Optional<URI> nextPageLink(List<String> linkHeaders) {
        for (String header : linkHeaders) {
            Matcher link = LINK.matcher(header);
            while (link.find()) {
                Matcher rel = REL.matcher(link.group(2));
                if (rel.find() && List.of(rel.group(1).trim().split("\\s+")).contains("next")) {
                    return Optional.of(URI.create(link.group(1).trim()));
                }
            }
        }
        return Optional.empty();
    }

    private record Page<T>(List<T> items, URI next) {
    }
}
