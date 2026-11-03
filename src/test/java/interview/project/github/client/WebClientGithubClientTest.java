package interview.project.github.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import interview.project.github.GithubJson;
import interview.project.github.config.GithubClientConfiguration;
import interview.project.github.config.GithubProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class WebClientGithubClientTest {

    @RegisterExtension
    static final WireMockExtension GITHUB = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().bindAddress("127.0.0.1"))
            .build();

    @Test
    void listUserRepositories_tokenConfigured_sendsBearerToken() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos")).willReturn(okJson("[]")));

        client("test-token").listUserRepositories("octocat").collectList().block();

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-token")));
    }

    @Test
    void listUserRepositories_tokenNotConfigured_sendsNoAuthorizationHeader() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos")).willReturn(okJson("[]")));

        client("").listUserRepositories("octocat").collectList().block();

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withoutHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void listUserRepositories_nextLinkPointsToAnotherHost_requestsSamePathFromConfiguredHost() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("repo-1", "octocat", false)))
                        .withHeader(HttpHeaders.LINK,
                                "<https://attacker.example.com/user/1/repos?page=2>; rel=\"next\"")));
        GITHUB.stubFor(get(urlEqualTo("/user/1/repos?page=2"))
                .willReturn(okJson(GithubJson.array(GithubJson.repository("repo-2", "octocat", false)))));

        List<GithubRepository> repositories = client("").listUserRepositories("octocat").collectList().block();

        assertThat(repositories).extracting(GithubRepository::name).containsExactly("repo-1", "repo-2");
        GITHUB.verify(1, getRequestedFor(urlEqualTo("/user/1/repos?page=2")));
    }

    private static WebClientGithubClient client(String token) {
        GithubProperties properties = new GithubProperties(URI.create(GITHUB.baseUrl()), token);
        WebClient webClient = new GithubClientConfiguration().githubWebClient(WebClient.builder(), properties);
        return new WebClientGithubClient(webClient, properties);
    }
}
