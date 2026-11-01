package interview.project.github;

/**
 * GitHub API response bodies for WireMock stubs. They carry extra fields like the real API does.
 */
public final class GithubJson {

    private GithubJson() {
    }

    public static String repository(String name, String owner, boolean fork) {
        return """
                {"id": 1296269, "name": "%1$s", "full_name": "%2$s/%1$s", "private": false, "fork": %3$s,
                 "description": "Repository %1$s", "url": "https://api.github.com/repos/%2$s/%1$s",
                 "owner": {"login": "%2$s", "id": 1, "type": "User", "site_admin": false}}"""
                .formatted(name, owner, fork);
    }

    public static String branch(String name, String sha) {
        return """
                {"name": "%1$s", "protected": false,
                 "commit": {"sha": "%2$s", "url": "https://api.github.com/repos/octocat/commits/%2$s"}}"""
                .formatted(name, sha);
    }

    public static String array(String... items) {
        return "[" + String.join(",", items) + "]";
    }
}
