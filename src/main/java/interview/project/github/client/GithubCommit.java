package interview.project.github.client;

import java.util.Objects;

public record GithubCommit(String sha) {

    public GithubCommit {
        Objects.requireNonNull(sha, "commit sha is missing");
    }
}
