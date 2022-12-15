package interview.project.Github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class GithubProxyApplication {
    public static void main(String[] args) {
        SpringApplication.run(GithubProxyApplication.class, args);
    }

    @Bean
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public HttpEntity<String> getAuthorizationTokenHttpEntity(@Value("${use_personal_access_key}") Boolean usePersonalAccessKey,
                                                              @Value("${github.personal.access.key}") String personalAccessKey) {
        HttpHeaders headers = new HttpHeaders();

        if (usePersonalAccessKey) {
            headers.set("Authorization", "Bearer " + personalAccessKey);
        }

        return new HttpEntity<>("", headers);
    }
}
