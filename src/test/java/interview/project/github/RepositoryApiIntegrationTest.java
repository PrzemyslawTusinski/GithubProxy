package interview.project.github;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static interview.project.github.GithubJson.array;
import static interview.project.github.GithubJson.branch;
import static interview.project.github.GithubJson.repository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepositoryApiIntegrationTest extends WireMockIntegrationTest {

    private static final String ENDPOINT = "/github_proxy/v1/repositories/{username}";
    private static final String USER = "octocat";
    private static final String RATE_LIMIT_MESSAGE =
            "GitHub API rate limit exceeded. Try again later or configure a GitHub token (GITHUB_TOKEN)";
    private static final int BRANCHES_DELAY_MILLIS = 300;

    @Test
    void getRepositories_userHasForksAndOwnRepositories_returnsOnlyNonForksWithBranches() throws Exception {
        stubRepositories(USER, okJson(array(
                repository("fork-1", USER, true),
                repository("hello-world", USER, false),
                repository("fork-2", USER, true),
                repository("spoon-knife", USER, false))));
        stubBranches(USER, "hello-world", okJson(array(branch("main", "sha-1"), branch("dev", "sha-2"))));
        stubBranches(USER, "spoon-knife", okJson(array(branch("main", "sha-3"))));

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
                .withQueryParam("per_page", equalTo("100"))
                .withHeader(HttpHeaders.ACCEPT, equalTo("application/vnd.github+json"))
                .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28")));
        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withQueryParam("per_page", equalTo("100")));
        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/spoon-knife/branches")));
        GITHUB.verify(0, getRequestedFor(urlPathMatching("/repos/octocat/fork-.*")));
    }

    @Test
    void getRepositories_userDoesNotExist_returns404WithErrorBody() throws Exception {
        stubRepositories("ghost-user", githubError(404));

        mockMvc.perform(get(ENDPOINT, "ghost-user").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(apiError(404, "GitHub user 'ghost-user' not found"));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/ghost-user/repos")));
    }

    @Test
    void getRepositories_repositoriesSpanTwoPages_returnsRepositoriesFromBothPages() throws Exception {
        stubRepositories(USER, okJson(array(repository("repo-1", USER, false)))
                .withHeader(HttpHeaders.LINK, link("/user/583231/repos?per_page=100&page=2", "next")
                        + ", " + link("/user/583231/repos?per_page=100&page=2", "last")));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/user/583231/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(array(repository("repo-2", USER, true), repository("repo-3", USER, false)))
                        .withHeader(HttpHeaders.LINK, link("/user/583231/repos?per_page=100&page=1", "prev")
                                + ", " + link("/user/583231/repos?per_page=100&page=1", "first"))));
        stubBranches(USER, "repo-1", okJson(array(branch("main", "sha-1"))));
        stubBranches(USER, "repo-3", okJson(array(branch("main", "sha-3"))));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"repositoryName": "repo-1", "ownerLogin": "octocat", "branches": [
                            {"name": "main", "lastCommitSha": "sha-1"}]},
                          {"repositoryName": "repo-3", "ownerLogin": "octocat", "branches": [
                            {"name": "main", "lastCommitSha": "sha-3"}]}
                        ]""", JsonCompareMode.STRICT));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos")));
        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/user/583231/repos")).withQueryParam("page", equalTo("2")));
    }

    @Test
    void getRepositories_branchesSpanTwoPages_returnsBranchesFromBothPages() throws Exception {
        stubRepositories(USER, okJson(array(repository("hello-world", USER, false))));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withQueryParam("page", absent())
                .willReturn(okJson(array(branch("branch-1", "sha-1"), branch("branch-2", "sha-2")))
                        .withHeader(HttpHeaders.LINK,
                                link("/repos/octocat/hello-world/branches?per_page=100&page=2", "next"))));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(array(branch("branch-3", "sha-3")))));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"repositoryName": "hello-world", "ownerLogin": "octocat", "branches": [
                            {"name": "branch-1", "lastCommitSha": "sha-1"},
                            {"name": "branch-2", "lastCommitSha": "sha-2"},
                            {"name": "branch-3", "lastCommitSha": "sha-3"}]}
                        ]""", JsonCompareMode.STRICT));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withQueryParam("page", absent()));
        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .withQueryParam("page", equalTo("2")));
    }

    @Test
    void getRepositories_fourRepositoriesWithSlowBranchEndpoints_fetchesBranchesInParallel() throws Exception {
        List<String> names = List.of("repo-1", "repo-2", "repo-3", "repo-4");
        stubRepositories(USER, okJson(array(names.stream()
                .map(name -> repository(name, USER, false))
                .toArray(String[]::new))));
        names.forEach(name -> stubBranches(USER, name,
                okJson(array(branch("main", "sha-" + name))).withFixedDelay(BRANCHES_DELAY_MILLIS)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].repositoryName").value(contains("repo-1", "repo-2", "repo-3", "repo-4")));

        List<Long> branchRequestTimes = GITHUB.getAllServeEvents().stream()
                .map(ServeEvent::getRequest)
                .filter(request -> request.getUrl().contains("/branches"))
                .map(request -> request.getLoggedDate().getTime())
                .toList();
        assertThat(branchRequestTimes).hasSize(4);
        // All four requests were sent before the first one could have been answered.
        assertThat(Collections.max(branchRequestTimes) - Collections.min(branchRequestTimes))
                .isLessThan(BRANCHES_DELAY_MILLIS);
    }

    @Test
    void getRepositories_githubReturns429_returns503WithErrorBody() throws Exception {
        stubRepositories(USER, githubError(429));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(apiError(503, RATE_LIMIT_MESSAGE));
    }

    @Test
    void getRepositories_githubReturns403WithNoRemainingRequests_returns503WithRetryAfter() throws Exception {
        long resetEpochSeconds = Instant.now().plusSeconds(120).getEpochSecond();
        stubRepositories(USER, githubError(403)
                .withHeader("X-RateLimit-Remaining", "0")
                .withHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds)));

        MvcResult result = mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(apiError(503, RATE_LIMIT_MESSAGE))
                .andReturn();

        long retryAfterSeconds = Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfterSeconds).isBetween(100L, 121L);
    }

    @Test
    void getRepositories_githubReturns403WithRetryAfter_returns503WithRetryAfter() throws Exception {
        // GitHub's secondary rate limit: Retry-After while the primary limit still has requests left.
        stubRepositories(USER, githubError(403)
                .withHeader(HttpHeaders.RETRY_AFTER, "60")
                .withHeader("X-RateLimit-Remaining", "4990"));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(apiError(503, RATE_LIMIT_MESSAGE));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos")));
    }

    @Test
    void getRepositories_branchesEndpointRateLimited_returns503() throws Exception {
        stubRepositories(USER, okJson(array(repository("hello-world", USER, false))));
        stubBranches(USER, "hello-world", githubError(403).withHeader("X-RateLimit-Remaining", "0"));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(apiError(503, RATE_LIMIT_MESSAGE));
    }

    @Test
    void getRepositories_githubReturns403WithoutRateLimitHeader_returns502() throws Exception {
        stubRepositories(USER, githubError(403).withHeader("X-RateLimit-Remaining", "4999"));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "GitHub API responded with status 403"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 422, 500, 503})
    void getRepositories_githubReturnsUnexpectedStatus_returns502(int githubStatus) throws Exception {
        stubRepositories(USER, githubError(githubStatus));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "GitHub API responded with status " + githubStatus));
    }

    @Test
    void getRepositories_branchesEndpointReturns500_returns502() throws Exception {
        stubRepositories(USER, okJson(array(repository("hello-world", USER, false))));
        stubBranches(USER, "hello-world", githubError(500));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "GitHub API responded with status 500"));
    }

    @Test
    void getRepositories_githubReturnsEmptyBody_returns502() throws Exception {
        stubRepositories(USER, aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "GitHub API returned an empty response"));
    }

    @Test
    void getRepositories_githubReturnsRepositoryWithoutOwner_returns502() throws Exception {
        stubRepositories(USER, okJson("[{\"name\": \"hello-world\", \"fork\": false}]"));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "Could not get a valid response from the GitHub API"));
    }

    @Test
    void getRepositories_githubClosesConnectionWithoutResponse_returns502() throws Exception {
        stubRepositories(USER, aResponse().withFault(Fault.EMPTY_RESPONSE));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "Could not get a valid response from the GitHub API"));
    }

    @Test
    void getRepositories_githubRedirects_returns502WithoutFollowingRedirect() throws Exception {
        stubRepositories(USER, aResponse().withStatus(301)
                .withHeader(HttpHeaders.LOCATION, githubBaseUrl() + "/moved/repos"));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/moved/repos")).willReturn(okJson("[]")));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(apiError(502, "GitHub API responded with status 301"));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos")));
        GITHUB.verify(0, getRequestedFor(urlPathEqualTo("/moved/repos")));
    }

    @Test
    void getRepositories_repositoriesPageOfAboutOneMegabyte_returnsAllRepositories() throws Exception {
        // About 1 MB in one page, well above WebClient's default 256 KB in-memory limit.
        String longDescription = "x".repeat(10_000);
        stubRepositories(USER, okJson(array(IntStream.rangeClosed(1, 100)
                .mapToObj(i -> repository("repo-" + i, USER, false, longDescription))
                .toArray(String[]::new))));
        GITHUB.stubFor(WireMock.get(urlPathMatching("/repos/octocat/[^/]+/branches"))
                .willReturn(okJson(array(branch("main", "sha-1")))));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100))
                .andExpect(jsonPath("$[99].repositoryName").value("repo-100"));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos")));
        GITHUB.verify(100, getRequestedFor(urlPathMatching("/repos/octocat/[^/]+/branches")));
    }

    @Test
    void getRepositories_githubRespondsAfterReadTimeout_returns504() throws Exception {
        stubRepositories(USER, okJson("[]").withFixedDelay(2_000));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isGatewayTimeout())
                .andExpect(apiError(504, "GitHub API did not respond in time"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/xml", "application/xml;charset=UTF-8", "text/xml"})
    void getRepositories_acceptHeaderIsXml_returns406WithJsonErrorBody(String accept) throws Exception {
        mockMvc.perform(get(ENDPOINT, USER).header(HttpHeaders.ACCEPT, accept))
                .andExpect(status().isNotAcceptable())
                .andExpect(apiError(406, "Acceptable representations: [application/json]."));

        GITHUB.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"*/*", "application/json"})
    void getRepositories_acceptHeaderMissingOrAllowsJson_returns200WithJson(String accept) throws Exception {
        stubRepositories(USER, okJson(array(repository("hello-world", USER, false))));
        stubBranches(USER, "hello-world", okJson(array(branch("main", "sha-1"))));
        MockHttpServletRequestBuilder request = get(ENDPOINT, USER);
        if (accept != null) {
            request.header(HttpHeaders.ACCEPT, accept);
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].repositoryName").value("hello-world"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-octocat", "octocat-", "octo--cat", "octo_cat", "octo.cat", "octo cat",
            "a234567890123456789012345678901234567890"})
    void getRepositories_invalidUsername_returns400WithoutCallingGithub(String username) throws Exception {
        mockMvc.perform(get(ENDPOINT, username))
                .andExpect(status().isBadRequest())
                .andExpect(apiError(400, "Invalid GitHub username: use 1-39 letters, digits or single hyphens, "
                        + "not starting or ending with a hyphen"));

        GITHUB.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void getRepositories_usernameWith39CharactersAndSingleHyphens_returns200() throws Exception {
        String username = "a23456789-123456789-123456789-123456789";
        stubRepositories(username, okJson("[]"));

        mockMvc.perform(get(ENDPOINT, username))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_usernameMissing_returns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/github_proxy/v1/repositories/"))
                .andExpect(status().isNotFound())
                .andExpect(apiError(404, "No static resource github_proxy/v1/repositories."));
    }

    @Test
    void getRepositories_tokenNotConfigured_sendsNoAuthorizationHeader() throws Exception {
        stubRepositories(USER, okJson("[]"));

        mockMvc.perform(get(ENDPOINT, USER)).andExpect(status().isOk());

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withoutHeader(HttpHeaders.AUTHORIZATION));
    }

    private static void stubRepositories(String user, ResponseDefinitionBuilder response) {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/" + user + "/repos")).willReturn(response));
    }

    private static void stubBranches(String user, String repo, ResponseDefinitionBuilder response) {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/" + user + "/" + repo + "/branches")).willReturn(response));
    }

    private static ResponseDefinitionBuilder githubError(int status) {
        return jsonResponse("{\"message\": \"Simulated GitHub error\"}", status);
    }

    private static ResultMatcher apiError(int status, String message) {
        return ResultMatcher.matchAll(
                content().contentType(MediaType.APPLICATION_JSON),
                content().json("{\"status\": %d, \"Message\": \"%s\"}".formatted(status, message),
                        JsonCompareMode.STRICT));
    }

    private static String link(String pathAndQuery, String rel) {
        return "<" + githubBaseUrl() + pathAndQuery + ">; rel=\"" + rel + "\"";
    }
}
