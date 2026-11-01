package interview.project.github.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI githubProxyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("GitHub Proxy API")
                .description("Lists the non-fork GitHub repositories of a user together with their branches "
                        + "and the SHA of the last commit on each branch.")
                .version("v1"));
    }
}
