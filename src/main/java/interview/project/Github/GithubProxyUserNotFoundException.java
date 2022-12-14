package interview.project.Github;

public class GithubProxyUserNotFoundException extends RuntimeException {
    GithubProxyUserNotFoundException() {
        super("User not found");
    }
}
