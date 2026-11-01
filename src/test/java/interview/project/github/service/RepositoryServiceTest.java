package interview.project.github.service;

import interview.project.github.api.BranchResponse;
import interview.project.github.api.RepositoryResponse;
import interview.project.github.client.GithubBranch;
import interview.project.github.client.GithubCommit;
import interview.project.github.client.GithubOwner;
import interview.project.github.client.GithubRepository;
import interview.project.github.config.GithubProperties;
import interview.project.github.exception.RateLimitExceededException;
import interview.project.github.exception.UpstreamTimeoutException;
import interview.project.github.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryServiceTest {

    private static final String USER = "octocat";

    private final FakeGithubClient githubClient = new FakeGithubClient();
    private final RepositoryService service = service(10);

    @Test
    void listRepositories_userHasForkAndNonForkRepositories_returnsOnlyNonForksWithBranches() {
        githubClient.addRepository(USER, repository("hello-world", USER, false));
        githubClient.addRepository(USER, repository("forked-library", USER, true));
        githubClient.addRepository(USER, repository("spoon-knife", USER, false));
        githubClient.addBranches(USER, "hello-world", List.of(branch("main", "sha-1"), branch("dev", "sha-2")));
        githubClient.addBranches(USER, "spoon-knife", List.of(branch("main", "sha-3")));

        List<RepositoryResponse> repositories = service.listRepositories(USER);

        assertThat(repositories).isEqualTo(List.of(
                new RepositoryResponse("hello-world", USER,
                        List.of(new BranchResponse("main", "sha-1"), new BranchResponse("dev", "sha-2"))),
                new RepositoryResponse("spoon-knife", USER, List.of(new BranchResponse("main", "sha-3")))));
        assertThat(githubClient.branchRequests())
                .containsExactlyInAnyOrder(USER + "/hello-world", USER + "/spoon-knife");
    }

    @Test
    void listRepositories_onlyForks_returnsEmptyListWithoutFetchingBranches() {
        githubClient.addRepository(USER, repository("fork-1", USER, true));
        githubClient.addRepository(USER, repository("fork-2", USER, true));

        List<RepositoryResponse> repositories = service.listRepositories(USER);

        assertThat(repositories).isEmpty();
        assertThat(githubClient.branchRequests()).isEmpty();
    }

    @Test
    void listRepositories_userWithoutRepositories_returnsEmptyList() {
        githubClient.addUserWithoutRepositories(USER);

        List<RepositoryResponse> repositories = service.listRepositories(USER);

        assertThat(repositories).isEmpty();
    }

    @Test
    void listRepositories_repositoryWithoutBranches_returnsEmptyBranchList() {
        githubClient.addRepository(USER, repository("empty-repository", USER, false));

        List<RepositoryResponse> repositories = service.listRepositories(USER);

        assertThat(repositories).isEqualTo(List.of(new RepositoryResponse("empty-repository", USER, List.of())));
    }

    @Test
    void listRepositories_ownerLoginDiffersFromRequestedUsername_usesOwnerLogin() {
        githubClient.addRepository("OctoCat", repository("hello-world", USER, false));
        githubClient.addBranches(USER, "hello-world", List.of(branch("main", "sha-1")));

        List<RepositoryResponse> repositories = service.listRepositories("OctoCat");

        assertThat(repositories).isEqualTo(List.of(
                new RepositoryResponse("hello-world", USER, List.of(new BranchResponse("main", "sha-1")))));
        assertThat(githubClient.branchRequests()).containsExactly(USER + "/hello-world");
    }

    @Test
    void listRepositories_branchesArriveInDifferentOrder_keepsGithubRepositoryOrder() {
        List.of("slow", "fast", "medium")
                .forEach(name -> githubClient.addRepository(USER, repository(name, USER, false)));
        githubClient.delayBranches(USER, "slow", Duration.ofMillis(60));
        githubClient.delayBranches(USER, "medium", Duration.ofMillis(30));

        List<RepositoryResponse> repositories = service.listRepositories(USER);

        assertThat(repositories).extracting(RepositoryResponse::repositoryName)
                .containsExactly("slow", "fast", "medium");
    }

    @Test
    void listRepositories_moreRepositoriesThanMaxConcurrency_fetchesBranchesConcurrentlyUpToLimit() {
        IntStream.rangeClosed(1, 6).mapToObj(i -> "repository-" + i).forEach(name -> {
            githubClient.addRepository(USER, repository(name, USER, false));
            githubClient.delayBranches(USER, name, Duration.ofMillis(50));
        });

        List<RepositoryResponse> repositories = service(3).listRepositories(USER);

        assertThat(repositories).hasSize(6);
        assertThat(githubClient.maxBranchRequestsInFlight()).isEqualTo(3);
    }

    @Test
    void listRepositories_userDoesNotExist_throwsUserNotFoundException() {
        assertThatThrownBy(() -> service.listRepositories("ghost"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("GitHub user 'ghost' not found");
    }

    @Test
    void listRepositories_branchesSlowerThanRequestTimeout_throwsUpstreamTimeoutException() {
        githubClient.addRepository(USER, repository("slow", USER, false));
        githubClient.delayBranches(USER, "slow", Duration.ofSeconds(2));
        RepositoryService serviceWithShortTimeout = service(10, Duration.ofMillis(100));

        assertThatThrownBy(() -> serviceWithShortTimeout.listRepositories(USER))
                .isInstanceOf(UpstreamTimeoutException.class)
                .hasMessage("GitHub API did not respond in time");
    }

    @Test
    void listRepositories_branchCallsFailWithCompositeError_throwsRateLimitExceededException() {
        githubClient.addRepository(USER, repository("hello-world", USER, false));
        githubClient.failBranches(USER, "hello-world", Exceptions.multiple(
                new RateLimitExceededException(null), new RateLimitExceededException(null)));

        assertThatThrownBy(() -> service.listRepositories(USER))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private RepositoryService service(int maxConcurrency) {
        return service(maxConcurrency, Duration.ofSeconds(5));
    }

    private RepositoryService service(int maxConcurrency, Duration requestTimeout) {
        GithubProperties properties =
                new GithubProperties(URI.create("http://localhost"), "", 100, maxConcurrency, requestTimeout);
        return new RepositoryService(githubClient, properties);
    }

    private static GithubRepository repository(String name, String owner, boolean fork) {
        return new GithubRepository(name, fork, new GithubOwner(owner));
    }

    private static GithubBranch branch(String name, String sha) {
        return new GithubBranch(name, new GithubCommit(sha));
    }
}
