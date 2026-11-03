package interview.project.github.exception;

public class RateLimitExceededException extends GithubApiException {

    public RateLimitExceededException() {
        super("GitHub API rate limit exceeded. Try again later or configure a GitHub token (GITHUB_TOKEN)");
    }
}
