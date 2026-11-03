package interview.project.github;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiDocumentationIntegrationTest extends WireMockIntegrationTest {

    private static final String RESPONSES = "$.paths['/github_proxy/v1/repositories/{username}'].get.responses";

    @Test
    void apiDocs_requested_describesRepositoriesEndpointAndErrorFormat() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("GitHub Proxy"))
                .andExpect(jsonPath(RESPONSES + "['200'].content['application/json'].schema").exists())
                .andExpect(jsonPath(RESPONSES + "['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath(RESPONSES + "['406'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.Message").exists());
    }
}
