package interview.project.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepository(String name, boolean fork, GithubOwner owner) {

    public GithubRepository {
        Objects.requireNonNull(name, "repository name is missing");
        Objects.requireNonNull(owner, "repository owner is missing");
    }
}
