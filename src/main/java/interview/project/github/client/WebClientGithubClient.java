package interview.project.github.client;

import interview.project.github.config.GithubProperties;
import interview.project.github.exception.GithubApiException;
import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import interview.project.github.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebClientGithubClient implements GithubClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientGithubClient.class);

    private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";

    // GitHub's maximum; with its default of 30 a listing would use about three times more of the rate limit.
    private static final int PAGE_SIZE = 100;

    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private final WebClient webClient;
    private final URI baseUrl;

    public WebClientGithubClient(WebClient githubWebClient, GithubProperties properties) {
        this.webClient = githubWebClient;
        this.baseUrl = properties.baseUrl();
    }

    @Override
    public Flux<GithubRepository> listUserRepositories(String username) {
        URI firstPage = firstPageUri("/users/{username}/repos", username);
        return fetchAllPages(firstPage, GithubRepository.class, () -> new UserNotFoundException(username));
    }

    @Override
    public Flux<GithubBranch> listBranches(String owner, String repository) {
        URI firstPage = firstPageUri("/repos/{owner}/{repository}/branches", owner, repository);
        return fetchAllPages(firstPage, GithubBranch.class, null);
    }

    private URI firstPageUri(String pathTemplate, Object... pathVariables) {
        return UriComponentsBuilder.fromUri(baseUrl)
                .path(pathTemplate)
                .queryParam("per_page", PAGE_SIZE)
                .encode()
                .buildAndExpand(pathVariables)
                .toUri();
    }

    private <T> Flux<T> fetchAllPages(URI firstPage, Class<T> type, Supplier<GithubApiException> notFoundError) {
        return fetchPage(firstPage, type, notFoundError)
                .expand(page -> Mono.justOrEmpty(nextPageLink(page.getHeaders()))
                        .flatMap(next -> fetchPage(next, type, notFoundError)))
                .concatMapIterable(ResponseEntity::getBody);
    }

    private <T> Mono<ResponseEntity<List<T>>> fetchPage(URI uri, Class<T> type,
                                                        Supplier<GithubApiException> notFoundError) {
        log.debug("GitHub request: GET {}", uri);
        return webClient.get()
                .uri(uri)
                .retrieve()
                // Redirects are not followed, so a 3xx is an error like a 4xx or 5xx.
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.createException().map(ex -> toApiException(uri, ex, notFoundError)))
                // A whole-page decode, unlike toEntityList, tells an empty body apart from an empty array.
                .toEntity(ParameterizedTypeReference.<List<T>>forType(
                        ResolvableType.forClassWithGenerics(List.class, type).getType()))
                .map(page -> {
                    if (page.getBody() == null) {
                        log.warn("GitHub API returned an empty body for GET {}", uri);
                        throw new UpstreamErrorException("GitHub API returned an empty response");
                    }
                    return page;
                })
                .onErrorMap(error -> !(error instanceof GithubApiException), error -> {
                    log.warn("GitHub API request failed: GET {}: {}", uri, error.toString());
                    return new UpstreamErrorException("Could not get a valid response from the GitHub API", error);
                });
    }

    private GithubApiException toApiException(URI uri, WebClientResponseException ex,
                                              Supplier<GithubApiException> notFoundError) {
        HttpStatusCode status = ex.getStatusCode();
        if (isRateLimited(status, ex.getHeaders())) {
            log.warn("GitHub API rate limit exceeded: GET {} returned {}", uri, status.value());
            return new RateLimitExceededException();
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND) && notFoundError != null) {
            log.info("GitHub API returned 404 for GET {}", uri);
            return notFoundError.get();
        }
        log.warn("GitHub API error: GET {} returned {} {}", uri, status.value(),
                LogFormatUtils.formatValue(ex.getResponseBodyAsString(), 300, true));
        return new UpstreamErrorException("GitHub API responded with status " + status.value());
    }

    // GitHub answers 403 instead of 429 when the primary rate limit is used up.
    private static boolean isRateLimited(HttpStatusCode status, HttpHeaders headers) {
        return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                || (status.isSameCodeAs(HttpStatus.FORBIDDEN) && "0".equals(headers.getFirst(RATE_LIMIT_REMAINING)));
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
        // Runs in expand, outside fetchPage's error mapping, so a bad link must become an upstream error here.
        try {
            URI uri = URI.create(next.group(1));
            return UriComponentsBuilder.fromUri(baseUrl)
                    .replacePath(uri.getRawPath())
                    .replaceQuery(uri.getRawQuery())
                    .build(true)
                    .toUri();
        } catch (IllegalArgumentException ex) {
            log.warn("GitHub API returned an invalid next link: {}", next.group(1));
            throw new UpstreamErrorException("GitHub API returned an invalid pagination link", ex);
        }
    }
}
