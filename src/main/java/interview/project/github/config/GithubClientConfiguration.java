package interview.project.github.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubClientConfiguration {

    private static final String GITHUB_MEDIA_TYPE = "application/vnd.github+json";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";

    /**
     * The connector (timeouts, redirect handling, gzip) comes from Boot's auto-configuration and the
     * {@code spring.http.reactiveclient.*} properties; only the GitHub-specific headers are added here.
     */
    @Bean
    public WebClient githubWebClient(WebClient.Builder builder, GithubProperties properties) {
        return builder
                .defaultHeader(HttpHeaders.ACCEPT, GITHUB_MEDIA_TYPE)
                .defaultHeader(API_VERSION_HEADER, API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, "github-proxy")
                .defaultHeaders(headers -> {
                    if (properties.hasToken()) {
                        headers.setBearerAuth(properties.token());
                    }
                })
                .build();
    }
}
