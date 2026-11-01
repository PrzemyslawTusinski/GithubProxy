package interview.project.github.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubClientConfiguration {

    private static final String GITHUB_MEDIA_TYPE = "application/vnd.github+json";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";

    /** A page of 100 repositories with all GitHub fields is well above WebClient's 256 KB default. */
    private static final int MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;

    @Bean
    public WebClient githubWebClient(WebClient.Builder builder, GithubProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.readTimeout());

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
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
