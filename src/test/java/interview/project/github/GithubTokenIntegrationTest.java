package interview.project.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static interview.project.github.GithubJson.array;
import static interview.project.github.GithubJson.branch;
import static interview.project.github.GithubJson.repository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "github.token=test-token")
class GithubTokenIntegrationTest extends WireMockIntegrationTest {

    @Test
    void getRepositories_tokenConfigured_sendsBearerTokenToEveryGithubEndpoint() throws Exception {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(array(repository("hello-world", "octocat", false)))));
        GITHUB.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .willReturn(okJson(array(branch("main", "sha-1")))));

        mockMvc.perform(get("/github_proxy/v1/repositories/{username}", "octocat"))
                .andExpect(status().isOk());

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-token")));
        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-token")));
    }
}
