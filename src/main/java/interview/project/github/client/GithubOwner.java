package interview.project.github.client;

import java.util.Objects;

public record GithubOwner(String login) {

    public GithubOwner {
        Objects.requireNonNull(login, "owner login is missing");
    }
}
