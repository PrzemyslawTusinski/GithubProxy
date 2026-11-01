package interview.project.github.service;

import interview.project.github.api.BranchResponse;
import interview.project.github.api.RepositoryResponse;
import interview.project.github.client.GithubClient;
import interview.project.github.client.GithubRepository;
import interview.project.github.config.GithubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    private final GithubClient githubClient;
    private final int maxConcurrency;

    public RepositoryService(GithubClient githubClient, GithubProperties properties) {
        this.githubClient = githubClient;
        this.maxConcurrency = properties.maxConcurrency();
    }

    /**
     * Lists the user's non-fork repositories with their branches. Branches of different repositories are fetched
     * concurrently (at most {@code github.max-concurrency} at a time); the result keeps GitHub's repository order.
     */
    public List<RepositoryResponse> listRepositories(String username) {
        log.info("Listing non-fork repositories of GitHub user '{}'", username);

        List<RepositoryResponse> repositories = githubClient.listUserRepositories(username)
                .filter(repository -> !repository.fork())
                .flatMapSequential(this::withBranches, maxConcurrency)
                .collectList()
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
