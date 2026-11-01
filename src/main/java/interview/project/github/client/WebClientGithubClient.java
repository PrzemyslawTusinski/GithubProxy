package interview.project.github.client;

import interview.project.github.config.GithubProperties;
import interview.project.github.exception.GithubApiException;
import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import interview.project.github.exception.UpstreamTimeoutException;
import interview.project.github.exception.UserNotFoundException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final WebClient webClient;
    private final URI baseUrl;
    private final int pageSize;

    public WebClientGithubClient(WebClient githubWebClient, GithubProperties properties) {
        this.webClient = githubWebClient;
        this.baseUrl = properties.baseUrl();
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
     * @param notFoundError error to raise when the first page answers HTTP 404, or {@code null} to treat 404 as an
     *                      upstream error; a 404 on a later page is always an upstream error
     */
    private <T> Flux<T> fetchAllPages(URI firstPage, ParameterizedTypeReference<List<T>> type,
                                      Supplier<GithubApiException> notFoundError) {
        return fetchPage(firstPage, type, notFoundError)
                .expand(page -> page.next() == null ? Mono.empty() : fetchPage(page.next(), type, null))
                .concatMapIterable(Page::items);
    }

    private <T> Mono<Page<T>> fetchPage(URI uri, ParameterizedTypeReference<List<T>> type,
                                        Supplier<GithubApiException> notFoundError) {
        log.debug("GitHub request: GET {}", uri);
        return webClient.get()
                .uri(uri)
                .retrieve()
                // The app disables redirect following (spring.http.reactiveclient.redirects), so this guard applies
                // in production too: a redirect is reported as an upstream error instead of being followed.
                .onStatus(HttpStatusCode::is3xxRedirection, ClientResponse::createException)
                .toEntity(type)
                .map(entity -> toPage(uri, entity))
                .onErrorMap(WebClientResponseException.class, ex -> toApiException(uri, ex.getStatusCode(),
                        ex.getHeaders(), ex.getResponseBodyAsString(), notFoundError))
                .onErrorMap(error -> !(error instanceof GithubApiException), error -> toApiException(uri, error));
    }

    private <T> Page<T> toPage(URI uri, ResponseEntity<List<T>> entity) {
        List<T> items = entity.getBody();
        if (items == null) {
            log.warn("GitHub API returned an empty body for GET {}", uri);
            throw new UpstreamErrorException("GitHub API returned an empty response");
        }
        URI next = nextPageLink(entity.getHeaders().getOrEmpty(HttpHeaders.LINK))
                .map(this::onBaseUrl)
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

    /**
     * A 403 is a rate limit when the primary limit is used up ({@code X-RateLimit-Remaining: 0}) or when it carries
     * {@code Retry-After}, which GitHub sends for secondary rate limits while requests still remain.
     */
    private static boolean isRateLimited(HttpStatusCode status, HttpHeaders headers) {
        return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                || (status.isSameCodeAs(HttpStatus.FORBIDDEN)
                        && ("0".equals(headers.getFirst(RATE_LIMIT_REMAINING))
                                || headers.containsKey(HttpHeaders.RETRY_AFTER)));
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
            // Plain long arithmetic: Instant.ofEpochSecond would throw for an out-of-range header value.
            return Duration.ofSeconds(Math.max(0, resetEpochSeconds - Instant.now().getEpochSecond()));
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

    /**
     * Netty's read timeout, a connect timeout, or a {@link java.util.concurrent.TimeoutException} such as Reactor
     * Netty's connection pool acquire timeout.
     */
    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof ConnectTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The token is sent with every request, so only the path and query of a pagination link are used and the request
     * always goes to the configured GitHub host.
     */
    private URI onBaseUrl(URI link) {
        return UriComponentsBuilder.fromUri(baseUrl)
                .replacePath(link.getRawPath())
                .replaceQuery(link.getRawQuery())
                .build(true)
                .toUri();
    }

    /**
     * Finds the {@code rel="next"} target in GitHub's {@code Link} header(s).
     */
    static Optional<URI> nextPageLink(List<String> linkHeaders) {
        for (String header : linkHeaders) {
            Matcher link = LINK.matcher(header);
            while (link.find()) {
                Matcher rel = REL.matcher(link.group(2));
                if (rel.find() && List.of(WHITESPACE.split(rel.group(1).trim())).contains("next")) {
                    return Optional.of(URI.create(link.group(1).trim()));
                }
            }
        }
        return Optional.empty();
    }

    private record Page<T>(List<T> items, URI next) {
    }
}
