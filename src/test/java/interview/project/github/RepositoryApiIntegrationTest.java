package interview.project.github;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static interview.project.github.GithubJson.array;
import static interview.project.github.GithubJson.branch;
import static interview.project.github.GithubJson.repository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepositoryApiIntegrationTest {

    private static final String ENDPOINT = "/github_proxy/v1/repositories/{username}";
    private static final String USER = "octocat";
    private static final String RATE_LIMIT_MESSAGE =
            "GitHub API rate limit exceeded. Try again later or configure a GitHub token (GITHUB_TOKEN)";
    private static final int BRANCHES_DELAY_MILLIS = 100;

    @RegisterExtension
    static final WireMockExtension GITHUB = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().bindAddress("127.0.0.1"))
            .build();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void githubApiProperties(DynamicPropertyRegistry registry) {
        registry.add("github.base-url", RepositoryApiIntegrationTest::githubBaseUrl);
    }

    @Test
    void getRepositories_userHasForksAndOwnRepositories_returnsOnlyNonForksWithBranches() throws Exception {
        stubRepositories(USER, okJson(array(
                repository("fork-1", USER, true),
                repository("hello-world", USER, false),
                repository("fork-2", USER, true),
                repository("spoon-knife", USER, false))));
        stubBranches("hello-world", okJson(array(branch("main", "sha-1"), branch("dev", "sha-2"))));
        stubBranches("spoon-knife", okJson(array(branch("main", "sha-3"))));

        mockMvc.perform(get(ENDPOINT, USER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [
                          {"repositoryName": "hello-world", "ownerLogin": "octocat", "branches": [
                            {"name": "main", "lastCommitSha": "sha-1"},
                            {"name": "dev", "lastCommitSha": "sha-2"}]},
                          {"repositoryName": "spoon-knife", "ownerLogin": "octocat", "branches": [
                            {"name": "main", "lastCommitSha": "sha-3"}]}
                        ]""", JsonCompareMode.STRICT));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withHeader(HttpHeaders.ACCEPT, equalTo("application/vnd.github+json"))
                .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28")));
        GITHUB.verify(0, getRequestedFor(urlPathMatching("/repos/octocat/fork-.*")));
    }

    @Test
    void getRepositories_userDoesNotExist_returns404WithErrorBody() throws Exception {
        stubRepositories("ghost-user", githubError(404));

        mockMvc.perform(get(ENDPOINT, "ghost-user").accept(MediaType.APPLICATION_JSON))
                .andExpect(apiError(404, "GitHub user 'ghost-user' not found"));
    }

    @Test
    void getRepositories_acceptHeaderIsXml_returns406WithJsonErrorBody() throws Exception {
        mockMvc.perform(get(ENDPOINT, USER).accept(MediaType.APPLICATION_XML))
                .andExpect(apiError(406, "Acceptable representations: [application/json]."));

        GITHUB.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void getRepositories_repositoriesSpanTwoPages_returnsRepositoriesFromBothPages() throws Exception {
        stubRepositories(USER, okJson(array(repository("repo-1", USER, false)))
                .withHeader(HttpHeaders.LINK, link("/user/583231/repos?page=2", "next")
                        + ", " + link("/user/583231/repos?page=2", "last")));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/user/583231/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(array(repository("repo-2", USER, true), repository("repo-3", USER, false)))
                        .withHeader(HttpHeaders.LINK, link("/user/583231/repos?page=1", "prev")
                                + ", " + link("/user/583231/repos?page=1", "first"))));
        stubBranches("repo-1", okJson(array(branch("main", "sha-1"))));
        stubBranches("repo-3", okJson(array(branch("main", "sha-3"))));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"repositoryName": "repo-1", "ownerLogin": "octocat", "branches": [
                            {"name": "main", "lastCommitSha": "sha-1"}]},
                          {"repositoryName": "repo-3", "ownerLogin": "octocat", "branches": [
                            {"name": "main", "lastCommitSha": "sha-3"}]}
                        ]""", JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_nextLinkIsMalformed_returns502WithErrorBody() throws Exception {
        stubRepositories(USER, okJson(array(repository("repo-1", USER, false)))
                .withHeader(HttpHeaders.LINK, link("/user/583231/repos?page=two words", "next")));
        stubBranches("repo-1", okJson(array(branch("main", "sha-1"))));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(502, "Could not get a valid response from the GitHub API"));
    }

    @Test
    void getRepositories_fourRepositoriesWithSlowBranchEndpoints_fetchesBranchesInParallel() throws Exception {
        List<String> names = List.of("repo-1", "repo-2", "repo-3", "repo-4");
        stubRepositories(USER, okJson(array(names.stream()
                .map(name -> repository(name, USER, false))
                .toArray(String[]::new))));
        names.forEach(name -> stubBranches(name,
                okJson(array(branch("main", "sha-" + name))).withFixedDelay(BRANCHES_DELAY_MILLIS)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].repositoryName").value(contains("repo-1", "repo-2", "repo-3", "repo-4")));

        List<Long> branchRequestTimes =
                GITHUB.findAll(getRequestedFor(urlPathMatching("/repos/octocat/[^/]+/branches"))).stream()
                        .map(request -> request.getLoggedDate().getTime())
                        .toList();
        assertThat(branchRequestTimes).hasSize(4);
        // All four requests were sent before the first one could have been answered.
        assertThat(Collections.max(branchRequestTimes) - Collections.min(branchRequestTimes))
                .isLessThan(BRANCHES_DELAY_MILLIS);
    }

    @Test
    void getRepositories_pageOf100RepositoriesOfAboutOneMegabyte_returnsAll100() throws Exception {
        String description = "x".repeat(10_000);
        stubRepositories(USER, okJson(array(IntStream.rangeClosed(1, 100)
                .mapToObj(i -> repository("repo-" + i, USER, false, description))
                .toArray(String[]::new))));
        GITHUB.stubFor(WireMock.get(urlPathMatching("/repos/octocat/[^/]+/branches"))
                .willReturn(okJson(array(branch("main", "sha-1")))));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(100)));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withQueryParam("per_page", equalTo("100")));
        GITHUB.verify(100, getRequestedFor(urlPathMatching("/repos/octocat/[^/]+/branches"))
                .withQueryParam("per_page", equalTo("100")));
    }

    @Test
    void getRepositories_githubReturns429_returns503WithErrorBody() throws Exception {
        stubRepositories(USER, githubError(429));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(503, RATE_LIMIT_MESSAGE));
    }

    @Test
    void getRepositories_githubReturns403WithNoRemainingRequests_returns503WithErrorBody() throws Exception {
        stubRepositories(USER, githubError(403).withHeader("X-RateLimit-Remaining", "0"));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(503, RATE_LIMIT_MESSAGE));
    }

    @Test
    void getRepositories_githubReturns500_returns502WithErrorBody() throws Exception {
        stubRepositories(USER, githubError(500));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(502, "GitHub API responded with status 500"));
    }

    @Test
    void getRepositories_githubReturns200WithHtml_returns502WithErrorBody() throws Exception {
        stubRepositories(USER, ok("<html><body>Unicorn!</body></html>")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(502, "Could not get a valid response from the GitHub API"));
    }

    @Test
    void getRepositories_githubReturns200WithEmptyBody_returns502WithErrorBody() throws Exception {
        stubRepositories(USER, aResponse().withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(502, "GitHub API returned an empty response"));
    }

    @Test
    void getRepositories_githubRedirects_returns502WithoutFollowingRedirect() throws Exception {
        stubRepositories(USER, aResponse().withStatus(301)
                .withHeader(HttpHeaders.LOCATION, githubBaseUrl() + "/elsewhere"));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/elsewhere")).willReturn(okJson("[]")));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(apiError(502, "GitHub API responded with status 301"));

        GITHUB.verify(0, getRequestedFor(urlPathEqualTo("/elsewhere")));
    }

    @Test
    void apiDocs_requested_describesRepositoriesEndpointAndErrorFormat() throws Exception {
        String responses = "$.paths['/github_proxy/v1/repositories/{username}'].get.responses";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("GitHub Proxy"))
                .andExpect(jsonPath(responses + "['200'].content['application/json'].schema").exists())
                .andExpect(jsonPath(responses + "['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath(responses + "['406'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.Message").exists());
    }

    private static String githubBaseUrl() {
        return "http://127.0.0.1:" + GITHUB.getPort();
    }

    private static void stubRepositories(String user, ResponseDefinitionBuilder response) {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/" + user + "/repos")).willReturn(response));
    }

    private static void stubBranches(String repo, ResponseDefinitionBuilder response) {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/" + USER + "/" + repo + "/branches")).willReturn(response));
    }

    private static ResponseDefinitionBuilder githubError(int status) {
        return jsonResponse("{\"message\": \"Simulated GitHub error\"}", status);
    }

    private static ResultMatcher apiError(int status, String message) {
        return ResultMatcher.matchAll(
                status().is(status),
                content().contentType(MediaType.APPLICATION_JSON),
                content().json("{\"status\": %d, \"Message\": \"%s\"}".formatted(status, message),
                        JsonCompareMode.STRICT));
    }

    private static String link(String pathAndQuery, String rel) {
        return "<" + githubBaseUrl() + pathAndQuery + ">; rel=\"" + rel + "\"";
    }
}
