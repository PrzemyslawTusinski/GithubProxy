package interview.project.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubOwner(String login) {

    public GithubOwner {
        Objects.requireNonNull(login, "owner login is missing");
    }
}
