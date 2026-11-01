package interview.project.github.client;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import interview.project.github.GithubJson;
import interview.project.github.config.GithubClientConfiguration;
import interview.project.github.config.GithubProperties;
import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import interview.project.github.exception.UpstreamTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebClientGithubClientTest {

    @RegisterExtension
    static final WireMockExtension GITHUB = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().bindAddress("127.0.0.1"))
            .build();

    private final WebClientGithubClient client = client("");

    @Test
    void nextPageLink_headerWithNextAndLastLinks_returnsNextLink() {
        List<String> linkHeader = List.of(
                "<https://api.github.com/user/1/repos?per_page=100&page=2>; rel=\"next\", "
                        + "<https://api.github.com/user/1/repos?per_page=100&page=5>; rel=\"last\"");

        assertThat(WebClientGithubClient.nextPageLink(linkHeader))
                .contains(URI.create("https://api.github.com/user/1/repos?per_page=100&page=2"));
    }

    @Test
    void nextPageLink_lastPageHeaderWithoutNextLink_returnsEmpty() {
        List<String> linkHeader = List.of(
                "<https://api.github.com/user/1/repos?per_page=100&page=4>; rel=\"prev\", "
                        + "<https://api.github.com/user/1/repos?per_page=100&page=1>; rel=\"first\"");

        assertThat(WebClientGithubClient.nextPageLink(linkHeader)).isEmpty();
    }

    @Test
    void nextPageLink_noLinkHeader_returnsEmpty() {
        assertThat(WebClientGithubClient.nextPageLink(List.of())).isEmpty();
    }

    @Test
    void listBranches_repositoryNameWithSpace_encodesItAsPathSegment() {
        GITHUB.stubFor(get(urlEqualTo("/repos/octocat/hello%20world/branches?per_page=100"))
                .willReturn(okJson(GithubJson.array(GithubJson.branch("main", "sha-1")))));

        List<GithubBranch> branches = client.listBranches("octocat", "hello world").collectList().block();

        assertThat(branches).isEqualTo(List.of(new GithubBranch("main", new GithubCommit("sha-1"))));
        GITHUB.verify(1, getRequestedFor(urlEqualTo("/repos/octocat/hello%20world/branches?per_page=100")));
    }

    @Test
    void listBranches_repositoryNotFound_throwsUpstreamErrorException() {
        GITHUB.stubFor(get(urlPathEqualTo("/repos/octocat/gone/branches")).willReturn(notFound()));

        assertThatThrownBy(() -> client.listBranches("octocat", "gone").collectList().block())
                .isInstanceOf(UpstreamErrorException.class)
                .hasMessage("GitHub API responded with status 404");
    }

    @Test
    void listUserRepositories_nextLinkPointsToAnotherHost_requestsSamePathFromConfiguredHost() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("repo-1", "octocat", false)))
                        .withHeader(HttpHeaders.LINK,
                                "<https://attacker.example.com/user/1/repos?page=2>; rel=\"next\"")));
        GITHUB.stubFor(get(urlEqualTo("/user/1/repos?page=2"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("repo-2", "octocat", false)))));

        List<GithubRepository> repositories = client.listUserRepositories("octocat").collectList().block();

        assertThat(repositories).extracting(GithubRepository::name).containsExactly("repo-1", "repo-2");
        GITHUB.verify(1, getRequestedFor(urlEqualTo("/user/1/repos?page=2")));
    }

    @Test
    void listUserRepositories_malformedJson_throwsUpstreamErrorException() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson("{\"this is\": not json")));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOf(UpstreamErrorException.class)
                .hasMessage("Could not get a valid response from the GitHub API");
    }

    @Test
    void listUserRepositories_connectionClosedWithoutResponse_throwsUpstreamErrorException() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOf(UpstreamErrorException.class)
                .hasMessage("Could not get a valid response from the GitHub API");
    }

    @Test
    void listUserRepositories_rateLimitedWithRetryAfterHeader_throwsRateLimitExceededWithThatDelay() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(status(429).withHeader(HttpHeaders.RETRY_AFTER, "30")));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        ex -> assertThat(ex.getRetryAfter()).contains(Duration.ofSeconds(30)));
    }

    @Test
    void listUserRepositoriesAndBranches_tokenConfigured_sendBearerToken() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("hello-world", "octocat", false)))));
        GITHUB.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .willReturn(okJson(GithubJson.array(GithubJson.branch("main", "sha-1")))));
        WebClientGithubClient clientWithToken = client("test-token");

        clientWithToken.listUserRepositories("octocat").collectList().block();
        clientWithToken.listBranches("octocat", "hello-world").collectList().block();

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-token")));
        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-token")));
    }

    @Test
    void listUserRepositories_connectionPoolAcquireTimesOut_throwsUpstreamTimeoutException() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos")).willReturn(okJson("[]").withFixedDelay(1_000)));
        ConnectionProvider provider = ConnectionProvider.builder("test")
                .maxConnections(1)
                .pendingAcquireTimeout(Duration.ofMillis(200))
                .build();
        try {
            WebClientGithubClient singleConnectionClient = client("",
                    WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider))));
            // The second request waits for the only connection, which the first one holds for a second.
            Flux<GithubRepository> twoConcurrentRequests = Flux.merge(
                    singleConnectionClient.listUserRepositories("octocat"),
                    singleConnectionClient.listUserRepositories("octocat"));

            assertThatThrownBy(() -> twoConcurrentRequests.collectList().block())
                    .isInstanceOf(UpstreamTimeoutException.class)
                    .hasMessage("GitHub API did not respond in time");
        } finally {
            provider.disposeLater().block();
        }
    }

    @Test
    void listUserRepositories_laterPageNotFound_throwsUpstreamErrorException() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("repo-1", "octocat", false)))
                        .withHeader(HttpHeaders.LINK, "<" + GITHUB.baseUrl() + "/user/1/repos?page=2>; rel=\"next\"")));
        GITHUB.stubFor(get(urlEqualTo("/user/1/repos?page=2")).willReturn(notFound()));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOf(UpstreamErrorException.class)
                .hasMessage("GitHub API responded with status 404");
        GITHUB.verify(1, getRequestedFor(urlEqualTo("/user/1/repos?page=2")));
    }

    @Test
    void listUserRepositories_rateLimitResetOutOfInstantRange_throwsRateLimitExceededException() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(status(403)
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset", "99999999999999999")));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        ex -> assertThat(ex.getRetryAfter()).isPresent());
    }

    private static WebClientGithubClient client(String token) {
        return client(token, WebClient.builder());
    }

    private static WebClientGithubClient client(String token, WebClient.Builder builder) {
        GithubProperties properties =
                new GithubProperties(URI.create(GITHUB.baseUrl()), token, 100, 10, Duration.ofSeconds(5));
        WebClient webClient = new GithubClientConfiguration().githubWebClient(builder, properties);
        return new WebClientGithubClient(webClient, properties);
    }
}
