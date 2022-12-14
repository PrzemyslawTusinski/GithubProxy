package interview.project.Github;

public class GithubProxyLimitExceeded extends RuntimeException {
    GithubProxyLimitExceeded() {
        super("Server exceeded limit request. Please try again soon or configure personal access token");
    }
}
