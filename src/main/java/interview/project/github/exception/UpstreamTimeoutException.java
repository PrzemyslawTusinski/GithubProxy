package interview.project.github.exception;

public class UpstreamTimeoutException extends GithubApiException {

    public UpstreamTimeoutException(Throwable cause) {
        super("GitHub API did not respond in time", cause);
    }
}
