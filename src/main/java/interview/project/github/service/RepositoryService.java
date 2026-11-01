package interview.project.github.service;

import interview.project.github.api.BranchResponse;
import interview.project.github.api.RepositoryResponse;
import interview.project.github.client.GithubClient;
import interview.project.github.client.GithubRepository;
import interview.project.github.config.GithubProperties;
import interview.project.github.exception.GithubApiException;
import interview.project.github.exception.UpstreamTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    private final GithubClient githubClient;
    private final int maxConcurrency;
    private final Duration requestTimeout;

    public RepositoryService(GithubClient githubClient, GithubProperties properties) {
        this.githubClient = githubClient;
        this.maxConcurrency = properties.maxConcurrency();
        this.requestTimeout = properties.requestTimeout();
    }

    /**
     * Lists the user's non-fork repositories with their branches. Branches of different repositories are fetched
     * concurrently (at most {@code github.max-concurrency} at a time); the result keeps GitHub's repository order.
     * The whole listing is bounded by {@code github.request-timeout}.
     */
    public List<RepositoryResponse> listRepositories(String username) {
        List<RepositoryResponse> repositories = githubClient.listUserRepositories(username)
                .filter(repository -> !repository.fork())
                .flatMapSequential(this::withBranches, maxConcurrency)
                .collectList()
                .timeout(requestTimeout)
                .onErrorMap(TimeoutException.class, error -> {
                    log.warn("Listing repositories of GitHub user '{}' took longer than {}", username, requestTimeout);
                    return new UpstreamTimeoutException(error);
                })
                // Branch calls failing at the same time can be merged into a reactor CompositeException.
                .onErrorMap(error -> Exceptions.unwrapMultiple(error).stream()
                        .filter(GithubApiException.class::isInstance)
                        .findFirst()
                        .orElse(error))
                .block();

        log.info("Found {} non-fork repositories of GitHub user '{}'", repositories.size(), username);
        return repositories;
    }

    private Mono<RepositoryResponse> withBranches(GithubRepository repository) {
        String ownerLogin = repository.owner().login();
        return githubClient.listBranches(ownerLogin, repository.name())
                .map(branch -> new BranchResponse(branch.name(), branch.commit().sha()))
                .collectList()
                .map(branches -> new RepositoryResponse(repository.name(), ownerLogin, branches));
    }
}
