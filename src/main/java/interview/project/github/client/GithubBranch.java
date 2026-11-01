package interview.project.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubBranch(String name, GithubCommit commit) {

    public GithubBranch {
        Objects.requireNonNull(name, "branch name is missing");
        Objects.requireNonNull(commit, "branch commit is missing");
    }
}
