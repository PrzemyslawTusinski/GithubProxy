package interview.project.github;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static interview.project.github.GithubJson.array;
import static interview.project.github.GithubJson.branch;
import static interview.project.github.GithubJson.repository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepositoryApiIntegrationTest extends WireMockIntegrationTest {

    private static final String ENDPOINT = "/github_proxy/v1/repositories/{username}";
    private static final String USER = "octocat";
    private static final String RATE_LIMIT_ERROR = """
            {"status": 503, "Message": "GitHub API rate limit exceeded. Try again later or configure a GitHub token (GITHUB_TOKEN)"}""";
    private static final long BRANCHES_DELAY_MILLIS = 500;

    @Test
    void getRepositories_userHasForksAndOwnRepositories_returnsOnlyNonForksWithBranches() throws Exception {
        stubRepositories(USER, array(
                repository("fork-1", USER, true),
                repository("hello-world", USER, false),
                repository("fork-2", USER, true),
                repository("spoon-knife", USER, false)));
        stubBranches(USER, "hello-world", array(branch("main", "sha-1"), branch("dev", "sha-2")));
        stubBranches(USER, "spoon-knife", array(branch("main", "sha-3")));

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
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/ghost-user/repos")).willReturn(githubError(404)));

        mockMvc.perform(get(ENDPOINT, "ghost-user").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status": 404, "Message": "GitHub user 'ghost-user' not found"}""", JsonCompareMode.STRICT));

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/ghost-user/repos")));
    }

    @Test
    void getRepositories_repositoriesSpanTwoPages_returnsRepositoriesFromBothPages() throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson(array(repository("repo-1", USER, false)))
                        .withHeader(HttpHeaders.LINK, link("/user/583231/repos?per_page=100&page=2", "next")
                                + ", " + link("/user/583231/repos?per_page=100&page=2", "last"))));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/user/583231/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(array(repository("repo-2", USER, true), repository("repo-3", USER, false)))
                        .withHeader(HttpHeaders.LINK, link("/user/583231/repos?per_page=100&page=1", "prev")
                                + ", " + link("/user/583231/repos?per_page=100&page=1", "first"))));
        stubBranches(USER, "repo-1", array(branch("main", "sha-1")));
        stubBranches(USER, "repo-3", array(branch("main", "sha-3")));

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
        stubRepositories(USER, array(repository("hello-world", USER, false)));
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
        stubRepositories(USER, array(names.stream()
                .map(name -> repository(name, USER, false))
                .toArray(String[]::new)));
        names.forEach(name -> GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/octocat/" + name + "/branches"))
                .willReturn(okJson(array(branch("main", "sha-" + name))).withFixedDelay((int) BRANCHES_DELAY_MILLIS))));
        warmUpHttpClient();

        long start = System.nanoTime();
        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].repositoryName").value(contains("repo-1", "repo-2", "repo-3", "repo-4")));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // Fetching the branches one after another would take at least 4 x 500 ms.
        assertThat(elapsedMillis).isLessThan(1500);
        GITHUB.verify(4, getRequestedFor(urlPathMatching("/repos/octocat/repo-\\d/branches")));
    }

    @Test
    void getRepositories_githubReturns429_returns503WithErrorBody() throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos")).willReturn(githubError(429)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(content().json(RATE_LIMIT_ERROR, JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_githubReturns403WithNoRemainingRequests_returns503WithRetryAfter() throws Exception {
        long resetEpochSeconds = Instant.now().plusSeconds(120).getEpochSecond();
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos")).willReturn(githubError(403)
                .withHeader("X-RateLimit-Remaining", "0")
                .withHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds))));

        MvcResult result = mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(RATE_LIMIT_ERROR, JsonCompareMode.STRICT))
                .andReturn();

        long retryAfterSeconds = Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfterSeconds).isBetween(100L, 121L);
    }

    @Test
    void getRepositories_branchesEndpointRateLimited_returns503() throws Exception {
        stubRepositories(USER, array(repository("hello-world", USER, false)));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .willReturn(githubError(403).withHeader("X-RateLimit-Remaining", "0")));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(RATE_LIMIT_ERROR, JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_githubReturns403WithoutRateLimitHeader_returns502() throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(githubError(403).withHeader("X-RateLimit-Remaining", "4999")));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status": 502, "Message": "GitHub API responded with status 403"}""", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 422, 500, 503})
    void getRepositories_githubReturnsUnexpectedStatus_returns502(int githubStatus) throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos")).willReturn(githubError(githubStatus)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status": 502, "Message": "GitHub API responded with status %d"}""".formatted(githubStatus),
                        JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_branchesEndpointReturns500_returns502() throws Exception {
        stubRepositories(USER, array(repository("hello-world", USER, false)));
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/octocat/hello-world/branches"))
                .willReturn(githubError(500)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"status": 502, "Message": "GitHub API responded with status 500"}""", JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_githubReturnsEmptyBody_returns502() throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, "application/json")));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"status": 502, "Message": "GitHub API returned an empty response"}""", JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_githubReturnsRepositoryWithoutOwner_returns502() throws Exception {
        stubRepositories(USER, "[{\"name\": \"hello-world\", \"fork\": false}]");

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"status": 502, "Message": "Could not get a valid response from the GitHub API"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_githubClosesConnectionWithoutResponse_returns502() throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"status": 502, "Message": "Could not get a valid response from the GitHub API"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_githubRespondsAfterReadTimeout_returns504() throws Exception {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos"))
                .willReturn(okJson("[]").withFixedDelay(2_000)));

        mockMvc.perform(get(ENDPOINT, USER))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status": 504, "Message": "GitHub API did not respond in time"}""", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/xml", "application/xml;charset=UTF-8", "text/xml"})
    void getRepositories_acceptHeaderIsXml_returns406WithJsonErrorBody(String accept) throws Exception {
        mockMvc.perform(get(ENDPOINT, USER).header(HttpHeaders.ACCEPT, accept))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status": 406, "Message": "Requested media type is not supported, \
                        this endpoint produces only application/json"}""", JsonCompareMode.STRICT));

        GITHUB.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"*/*", "application/json"})
    void getRepositories_acceptHeaderMissingOrAllowsJson_returns200WithJson(String accept) throws Exception {
        stubRepositories(USER, array(repository("hello-world", USER, false)));
        stubBranches(USER, "hello-world", array(branch("main", "sha-1")));
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
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.Message").value(startsWith("Invalid GitHub username")));

        GITHUB.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void getRepositories_usernameWith39CharactersAndSingleHyphens_returns200() throws Exception {
        String username = "a23456789-123456789-123456789-123456789";
        stubRepositories(username, "[]");

        mockMvc.perform(get(ENDPOINT, username))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_usernameMissing_returns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/github_proxy/v1/repositories/"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status": 404, "Message": "No endpoint GET /github_proxy/v1/repositories/"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    void getRepositories_tokenNotConfigured_sendsNoAuthorizationHeader() throws Exception {
        stubRepositories(USER, "[]");

        mockMvc.perform(get(ENDPOINT, USER)).andExpect(status().isOk());

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withoutHeader(HttpHeaders.AUTHORIZATION));
    }

    private void warmUpHttpClient() throws Exception {
        stubRepositories("warm-up", "[]");
        mockMvc.perform(get(ENDPOINT, "warm-up")).andExpect(status().isOk());
    }

    private static void stubRepositories(String username, String body) {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/users/" + username + "/repos"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(okJson(body)));
    }

    private static void stubBranches(String owner, String repository, String body) {
        GITHUB.stubFor(WireMock.get(urlPathEqualTo("/repos/" + owner + "/" + repository + "/branches"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(okJson(body)));
    }

    private static ResponseDefinitionBuilder githubError(int status) {
        return aResponse()
                .withStatus(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody("{\"message\": \"Simulated GitHub error\"}");
    }

    private static String link(String pathAndQuery, String rel) {
        return "<" + githubBaseUrl() + pathAndQuery + ">; rel=\"" + rel + "\"";
    }
}
