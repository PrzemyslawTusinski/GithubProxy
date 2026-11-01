package interview.project.github.exception;

public abstract class GithubApiException extends RuntimeException {

    protected GithubApiException(String message) {
        super(message);
    }

    protected GithubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
