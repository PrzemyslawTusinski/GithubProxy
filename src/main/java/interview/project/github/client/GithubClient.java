package interview.project.github.client;

import reactor.core.publisher.Flux;

public interface GithubClient {

    Flux<GithubRepository> listUserRepositories(String username);

    Flux<GithubBranch> listBranches(String owner, String repository);
}
