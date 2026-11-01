package interview.project.github.client;

import reactor.core.publisher.Flux;

/**
 * Read-only access to the parts of the GitHub REST API this application needs.
 * Implementations follow pagination, so the returned streams contain all items.
 */
public interface GithubClient {

    /**
     * Repositories owned by the user, in the order GitHub returns them.
     *
     * @throws interview.project.github.exception.UserNotFoundException (as an error signal) when the user does not exist
     */
    Flux<GithubRepository> listUserRepositories(String username);

    Flux<GithubBranch> listBranches(String owner, String repository);
}
