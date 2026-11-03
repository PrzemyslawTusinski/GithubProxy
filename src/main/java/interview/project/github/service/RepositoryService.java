package interview.project.github.service;

import interview.project.github.api.BranchResponse;
import interview.project.github.api.RepositoryResponse;
import interview.project.github.client.GithubClient;
import interview.project.github.client.GithubRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    // Bounds the parallel branch requests to stay polite to GitHub.
    private static final int MAX_CONCURRENCY = 10;

    private final GithubClient githubClient;

    public RepositoryService(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    public List<RepositoryResponse> listRepositories(String username) {
        List<RepositoryResponse> repositories = githubClient.listUserRepositories(username)
                .filter(repository -> !repository.fork())
                // flatMapSequential rather than flatMap, so the result keeps GitHub's repository order.
                .flatMapSequential(this::withBranches, MAX_CONCURRENCY)
                .collectList()
                // Branch calls failing at the same time can be merged into a reactor CompositeException.
                .onErrorMap(error -> Exceptions.unwrapMultiple(error).get(0))
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
