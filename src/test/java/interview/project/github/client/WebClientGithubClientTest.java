package interview.project.github.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import interview.project.github.GithubJson;
import interview.project.github.config.GithubClientConfiguration;
import interview.project.github.config.GithubProperties;
import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamErrorException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebClientGithubClientTest {

    private static final WireMockServer GITHUB =
            new WireMockServer(wireMockConfig().dynamicPort().bindAddress("127.0.0.1"));

    private WebClientGithubClient client;

    @BeforeAll
    static void startGithub() {
        GITHUB.start();
    }

    @AfterAll
    static void stopGithub() {
        GITHUB.stop();
    }

    @BeforeEach
    void setUp() {
        GITHUB.resetAll();
        GithubProperties properties = new GithubProperties(
                new GithubProperties.Api(URI.create("http://127.0.0.1:" + GITHUB.port())),
                "", Duration.ofSeconds(2), Duration.ofSeconds(2), 100, 10);
        WebClient webClient = new GithubClientConfiguration().githubWebClient(WebClient.builder(), properties);
        client = new WebClientGithubClient(webClient, properties);
    }

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
        GITHUB.stubFor(get(urlPathEqualTo("/repos/octocat/gone/branches"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.listBranches("octocat", "gone").collectList().block())
                .isInstanceOf(UpstreamErrorException.class)
                .hasMessage("GitHub API responded with status 404");
    }

    @Test
    void listUserRepositories_nextLinkPointsToAnotherHost_throwsUpstreamErrorException() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("hello-world", "octocat", false)))
                        .withHeader("Link", "<https://attacker.example.com/user/1/repos?page=2>; rel=\"next\"")));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOf(UpstreamErrorException.class)
                .hasMessage("GitHub API returned a pagination link to an unexpected host");
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
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> client.listUserRepositories("octocat").collectList().block())
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        ex -> assertThat(ex.getRetryAfter()).contains(Duration.ofSeconds(30)));
    }
}
