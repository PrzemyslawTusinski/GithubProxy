package interview.project.github;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@OpenAPIDefinition(info = @Info(title = "GitHub Proxy", version = "v1"))
@SpringBootApplication
@ConfigurationPropertiesScan
public class GithubProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(GithubProxyApplication.class, args);
    }
}
