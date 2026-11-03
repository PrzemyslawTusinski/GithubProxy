package interview.project.github.service;

import interview.project.github.api.RepositoryResponse;
import interview.project.github.client.GithubBranch;
import interview.project.github.client.GithubClient;
import interview.project.github.client.GithubCommit;
import interview.project.github.client.GithubOwner;
import interview.project.github.client.GithubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryServiceTest {

    private static final String USER = "octocat";

    private final GithubClient githubClient = mock(GithubClient.class);
    private final RepositoryService service = new RepositoryService(githubClient);

    @Test
    void listRepositories_firstRepositoryBranchesAnswerLast_keepsGithubOrder() {
        when(githubClient.listUserRepositories(USER)).thenReturn(Flux.just(repository("slow"), repository("fast")));
        when(githubClient.listBranches(USER, "slow"))
                .thenReturn(Flux.just(branch("main", "sha-1")).delaySubscription(Duration.ofMillis(20)));
        when(githubClient.listBranches(USER, "fast")).thenReturn(Flux.just(branch("main", "sha-2")));

        assertThat(service.listRepositories(USER))
                .extracting(RepositoryResponse::repositoryName)
                .containsExactly("slow", "fast");
    }

    @Test
    void listRepositories_branchCallsFailWithCompositeError_throwsResponseStatusException() {
        when(githubClient.listUserRepositories(USER)).thenReturn(Flux.just(repository("hello-world")));
        when(githubClient.listBranches(USER, "hello-world")).thenReturn(Flux.error(Exceptions.multiple(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE),
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))));

        assertThatThrownBy(() -> service.listRepositories(USER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static GithubRepository repository(String name) {
        return new GithubRepository(name, false, new GithubOwner(USER));
    }

    private static GithubBranch branch(String name, String sha) {
        return new GithubBranch(name, new GithubCommit(sha));
    }
}
