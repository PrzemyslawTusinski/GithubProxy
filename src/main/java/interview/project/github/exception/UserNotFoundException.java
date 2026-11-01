package interview.project.github.exception;

public class UserNotFoundException extends GithubApiException {

    public UserNotFoundException(String username) {
        super("GitHub user '%s' not found".formatted(username));
    }
}
