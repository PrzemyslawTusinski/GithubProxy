package interview.project.Github;

import com.google.gson.Gson;
import interview.project.Github.data.*;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest

public class GithubProxyServiceUnitTest {
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private GithubProxyService service;

    private MockRestServiceServer mockServer;

    private final String USER1 = "user1";
    private final String REPO1 = "repo1";
    private final String REPO2 = "repo2";

    private final String BRANCH1 = "branch1";
    private final String SHA1 = "sha1";
    private final String BRANCH2 = "branch2";
    private final String SHA2 = "sha2";

    @BeforeEach
    public void init() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }
    @SneakyThrows
    @Test
    public void testListRepositories_Given2ReposWith2NonForkBranchesEach_ShouldReturnThatData() {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://api.github.com/users/user1/repos")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(getTwoMockedRepositoryResponse(false), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(new URI("https://api.github.com/repos/user1/repo1/branches")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(getTwoMockedBranchesResponse(), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(new URI("https://api.github.com/repos/user1/repo2/branches")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(getTwoMockedBranchesResponse(), MediaType.APPLICATION_JSON));


        List<ProxyRepositories> repos = service.listRepositories(USER1);

        assertEquals(2, repos.size ());
        assertEquals(REPO1, repos.get(0).getRepositoryName());
        assertEquals(USER1, repos.get(0).getOwnerLogon());
        assertEquals(2,repos.get(0).getBranches().size());
        assertEquals(SHA1,repos.get(0).getBranches().get(0).getLastCommitSha());
        assertEquals(BRANCH1,repos.get(0).getBranches().get(0).getName());
        assertEquals(SHA2,repos.get(0).getBranches().get(1).getLastCommitSha());
        assertEquals(BRANCH2,repos.get(0).getBranches().get(1).getName());

        assertEquals(REPO2, repos.get(1).getRepositoryName());
        assertEquals(USER1, repos.get(1).getOwnerLogon());
        assertEquals(2,repos.get(1).getBranches().size());
        assertEquals(SHA1,repos.get(1).getBranches().get(0).getLastCommitSha());
        assertEquals(BRANCH1,repos.get(1).getBranches().get(0).getName());
        assertEquals(SHA2,repos.get(1).getBranches().get(1).getLastCommitSha());
        assertEquals(BRANCH2,repos.get(1).getBranches().get(1).getName());
    }

    @SneakyThrows
    @Test
    public void testListRepositories_GivenReposWithForksOnly_ShouldReturnEmptyList() {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://api.github.com/users/user1/repos")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(getTwoMockedRepositoryResponse(true), MediaType.APPLICATION_JSON));

        List<ProxyRepositories> repos = service.listRepositories(USER1);

        assertEquals(0, repos.size());
    }

    public String getTwoMockedBranchesResponse() {
        GithubBranch branch1 = GithubBranch.builder()
                .name(BRANCH1)
                .commit(GithubCommit.builder().sha(SHA1).build())
                .build();

        GithubBranch branch2 = GithubBranch.builder()
                .name(BRANCH2)
                .commit(GithubCommit.builder().sha(SHA2).build())
                .build();

        List<GithubBranch> repos = asList(branch1, branch2);

        return new Gson().toJson(repos);
    }
    public String getTwoMockedRepositoryResponse(boolean areForks) {
        GithubRepository repository1 = GithubRepository.builder()
                .name(REPO1)
                .fork(areForks)
                .owner(GithubOwner.builder().login(USER1).build())
                .build();
        GithubRepository repository2 = GithubRepository.builder()
                .name(REPO2)
                .fork(areForks)
                .owner(GithubOwner.builder().login(USER1).build())
                .build();

        List<GithubRepository> repos = asList(repository1, repository2);

        return new Gson().toJson(repos);
    }

    @SneakyThrows
    @Test()
    public void testListRepositories_HttpStatusNotFound_ThrowsUserNotFound() {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://api.github.com/users/user1/repos")))
                .andExpect(method(HttpMethod.GET)).
        andRespond((response) -> { throw new HttpClientErrorException(HttpStatus.NOT_FOUND);});

        Exception exception = assertThrows(GithubProxyUserNotFoundException.class, () -> service.listRepositories(USER1));

        assertEquals("User not found", exception.getMessage());
    }

    @SneakyThrows
    @Test()
    public void testListRepositories_HttpStatusFORBIDDEN_ThrowsLimitExceeded() {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://api.github.com/users/user1/repos")))
                .andExpect(method(HttpMethod.GET)).
                andRespond((response) -> { throw new HttpClientErrorException(HttpStatus.FORBIDDEN);});

        Exception exception = assertThrows(GithubProxyLimitExceeded.class, () -> service.listRepositories(USER1));

        assertEquals("Server exceeded limit request. Please try again soon or configure personal access token", exception.getMessage());
    }
}