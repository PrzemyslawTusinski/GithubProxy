package interview.project.github.exception;

/**
 * GitHub answered with an unexpected status or an unusable body, or could not be reached.
 */
public class UpstreamErrorException extends GithubApiException {

    public UpstreamErrorException(String message) {
        super(message);
    }

    public UpstreamErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
