package interview.project.github.client;

import java.util.Objects;

public record GithubRepository(String name, boolean fork, GithubOwner owner) {

    public GithubRepository {
        Objects.requireNonNull(name, "repository name is missing");
        Objects.requireNonNull(owner, "repository owner is missing");
    }
}
