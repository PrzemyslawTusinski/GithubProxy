package interview.project.github.client;

import java.util.Objects;

public record GithubBranch(String name, GithubCommit commit) {

    public GithubBranch {
        Objects.requireNonNull(name, "branch name is missing");
        Objects.requireNonNull(commit, "branch commit is missing");
    }
}
