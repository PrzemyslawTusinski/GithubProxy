package interview.project.github.service;

import interview.project.github.api.BranchResponse;
import interview.project.github.api.RepositoryResponse;
import interview.project.github.client.GithubBranch;
import interview.project.github.client.GithubCommit;
import interview.project.github.client.GithubOwner;
import interview.project.github.client.GithubRepository;
import interview.project.github.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryServiceTest {

    private static final String USER = "octocat";

    private final FakeGithubClient githubClient = new FakeGithubClient();
    private final RepositoryService service = new RepositoryService(githubClient);

    @Test
    void listRepositories_forksAndOwnRepositories_returnsOnlyNonForksWithMappedBranches() {
        githubClient.addRepository(USER, repository("hello-world", false));
        githubClient.addRepository(USER, repository("forked", true));
        githubClient.addRepository(USER, repository("spoon-knife", false));
        githubClient.addBranches(USER, "hello-world", Flux.just(branch("main", "sha-1"), branch("dev", "sha-2")));
        githubClient.addBranches(USER, "forked", Flux.just(branch("main", "sha-3")));
        githubClient.addBranches(USER, "spoon-knife", Flux.just(branch("main", "sha-4")));

        assertThat(service.listRepositories(USER)).containsExactly(
                new RepositoryResponse("hello-world", USER,
                        List.of(new BranchResponse("main", "sha-1"), new BranchResponse("dev", "sha-2"))),
                new RepositoryResponse("spoon-knife", USER, List.of(new BranchResponse("main", "sha-4"))));
    }

    @Test
    void listRepositories_firstRepositoryBranchesAnswerLast_keepsGithubOrder() {
        githubClient.addRepository(USER, repository("slow", false));
        githubClient.addRepository(USER, repository("fast", false));
        githubClient.addBranches(USER, "slow",
                Flux.just(branch("main", "sha-1")).delaySubscription(Duration.ofMillis(20)));
        githubClient.addBranches(USER, "fast", Flux.just(branch("main", "sha-2")));

        assertThat(service.listRepositories(USER))
                .extracting(RepositoryResponse::repositoryName)
                .containsExactly("slow", "fast");
    }

    @Test
    void listRepositories_branchCallsFailWithCompositeError_throwsRateLimitExceededException() {
        githubClient.addRepository(USER, repository("hello-world", false));
        githubClient.addBranches(USER, "hello-world", Flux.error(Exceptions.multiple(
                new RateLimitExceededException(), new RateLimitExceededException())));

        assertThatThrownBy(() -> service.listRepositories(USER))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private static GithubRepository repository(String name, boolean fork) {
        return new GithubRepository(name, fork, new GithubOwner(USER));
    }

    private static GithubBranch branch(String name, String sha) {
        return new GithubBranch(name, new GithubCommit(sha));
    }
}
