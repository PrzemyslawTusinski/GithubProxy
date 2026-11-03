package interview.project.github.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "github")
public record GithubProperties(URI baseUrl, String token) {
}
