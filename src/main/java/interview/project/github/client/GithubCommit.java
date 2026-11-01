package interview.project.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubCommit(String sha) {

    public GithubCommit {
        Objects.requireNonNull(sha, "commit sha is missing");
    }
}
