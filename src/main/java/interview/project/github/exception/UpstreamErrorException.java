package interview.project.github.exception;

public class UpstreamErrorException extends GithubApiException {

    public UpstreamErrorException(String message) {
        super(message);
    }

    public UpstreamErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
