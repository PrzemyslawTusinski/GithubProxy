package interview.project.github.config;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the GitHub {@link WebClient} bean as Spring builds it from properties and Boot's HTTP client
 * auto-configuration, without starting the whole application.
 */
class GithubClientConfigurationTest {

    @RegisterExtension
    static final WireMockExtension GITHUB = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().bindAddress("127.0.0.1"))
            .build();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class,
                    ClientHttpConnectorAutoConfiguration.class, CodecsAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(GithubClientConfiguration.class);

    @Test
    void githubWebClient_tokenPropertySet_sendsBearerTokenAndAcceptsGzip() {
        GITHUB.stubFor(get(urlPathEqualTo("/users/octocat/repos")).willReturn(okJson("[]")));

        contextRunner.withPropertyValues("github.token=test-token", "github.base-url=" + GITHUB.baseUrl(),
                        "github.page-size=100", "github.max-concurrency=10", "github.request-timeout=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WebClient webClient = context.getBean(WebClient.class);

                    webClient.get()
                            .uri(GITHUB.baseUrl() + "/users/octocat/repos")
                            .retrieve()
                            .toBodilessEntity()
                            .block();
                });

        GITHUB.verify(1, getRequestedFor(urlPathEqualTo("/users/octocat/repos"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-token"))
                .withHeader(HttpHeaders.ACCEPT_ENCODING, containing("gzip")));
    }
}
