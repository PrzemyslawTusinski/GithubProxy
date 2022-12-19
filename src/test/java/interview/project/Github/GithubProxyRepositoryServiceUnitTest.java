package interview.project.Github;

import interview.project.Github.data.GithubOwner;
import interview.project.Github.data.GithubRepository;
import interview.project.Github.data.ProxyBranch;
import interview.project.Github.data.ProxyRepositories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GithubProxyRepositoryServiceUnitTest {
    @Mock
    private RestTemplate restTemplateMock;

    @Mock
    private GithubProxyBranchService githubProxyBranchServiceMock;

    @Mock
    private HttpEntity<String> authorizationTokenHttpEntity;

    @InjectMocks
    private GithubProxyRepositoryService service;

    private final String USER1 = "user1";
    private final String REPO1 = "repo1";
    private final String REPO2 = "repo2";

    private final String BRANCH1 = "branch1";
    private final String SHA1 = "sha1";
    private final String BRANCH2 = "branch2";
    private final String SHA2 = "sha2";

    @Test
    public void testListRepositories_Given2ReposWith2NonForkBranchesEach_ShouldReturnThatData() {
        when(restTemplateMock.exchange(
                "https://api.github.com/users/user1/repos",
                HttpMethod.GET,
                authorizationTokenHttpEntity,
                GithubRepository[].class))
        .thenReturn(ResponseEntity.ok(getTwoMockedRepositoryResponse(false)));

        var proxyBranches = List.of(new ProxyBranch(BRANCH1, SHA1), new ProxyBranch(BRANCH2, SHA2));

        when(githubProxyBranchServiceMock.listBranchesForRepository(USER1, REPO1))
                .thenReturn(proxyBranches);

        when(githubProxyBranchServiceMock.listBranchesForRepository(USER1, REPO2))
                .thenReturn(List.of());

        List<ProxyRepositories> expectedRepos = List.of(
                new ProxyRepositories(REPO1, USER1, proxyBranches),
                new ProxyRepositories(REPO2, USER1, List.of()));

        List<ProxyRepositories> repos = service.listRepositories(USER1);

        assertThat(expectedRepos).isEqualTo(repos);
    }

    @Test
    public void testListRepositories_GivenReposWithForksOnly_ShouldReturnEmptyList() {
        when(restTemplateMock.exchange(
                "https://api.github.com/users/user1/repos",
                HttpMethod.GET,
                authorizationTokenHttpEntity,
                GithubRepository[].class))
                .thenReturn(ResponseEntity.ok(getTwoMockedRepositoryResponse(true)));

        List<ProxyRepositories> repos = service.listRepositories(USER1);

        assertEquals(0, repos.size());
    }

    public GithubRepository[] getTwoMockedRepositoryResponse(boolean areForks) {
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
        return new GithubRepository[] {repository1, repository2};
    }

    @Test()
    public void testListRepositories_HttpStatusNotFound_ThrowsUserNotFound() {
        when(restTemplateMock.exchange(
                "https://api.github.com/users/user1/repos",
                HttpMethod.GET,
                authorizationTokenHttpEntity,
                GithubRepository[].class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        Exception exception = assertThrows(GithubProxyUserNotFoundException.class, () -> service.listRepositories(USER1));

        assertEquals("User not found", exception.getMessage());
    }

    @Test()
    public void testListRepositories_HttpStatusFORBIDDEN_ThrowsLimitExceeded() {
        when(restTemplateMock.exchange(
                "https://api.github.com/users/user1/repos",
                HttpMethod.GET,
                authorizationTokenHttpEntity,
                GithubRepository[].class))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        Exception exception = assertThrows(GithubProxyLimitExceeded.class, () -> service.listRepositories(USER1));

        assertEquals("Server exceeded limit request. Please try again soon or configure personal access token", exception.getMessage());
    }
}