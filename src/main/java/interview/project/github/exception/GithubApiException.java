package interview.project.github.exception;

/**
 * Base class of the errors raised while talking to the GitHub API.
 */
public abstract class GithubApiException extends RuntimeException {

    protected GithubApiException(String message) {
        super(message);
    }

    protected GithubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
