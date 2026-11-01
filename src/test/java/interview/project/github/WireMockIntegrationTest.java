package interview.project.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Starts the whole application against a WireMock server standing in for api.github.com.
 * One WireMock instance is shared by all subclasses, so Spring can reuse the application context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"github.token=", "github.request-timeout=5s",
        "spring.http.reactiveclient.read-timeout=1s"})
abstract class WireMockIntegrationTest {

    protected static final WireMockServer GITHUB =
            new WireMockServer(wireMockConfig().dynamicPort().bindAddress("127.0.0.1"));

    static {
        GITHUB.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void githubApiProperties(DynamicPropertyRegistry registry) {
        registry.add("github.base-url", WireMockIntegrationTest::githubBaseUrl);
    }

    protected static String githubBaseUrl() {
        return "http://127.0.0.1:" + GITHUB.port();
    }

    @BeforeEach
    void resetGithub() {
        GITHUB.resetAll();
    }
}
