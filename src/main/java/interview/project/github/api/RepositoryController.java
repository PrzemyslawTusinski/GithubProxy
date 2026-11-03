package interview.project.github.api;

import interview.project.github.service.RepositoryService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/github_proxy/v1")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    // Springdoc cannot infer these: the statuses come from ApiExceptionHandler, not from this method.
    @ApiResponse(responseCode = "200", description = "Non-fork repositories of the user with their branches")
    @ApiResponse(responseCode = "404", description = "The GitHub user does not exist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Accept does not allow application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @GetMapping(path = "/repositories/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RepositoryResponse> listRepositories(@PathVariable String username) {
        return repositoryService.listRepositories(username);
    }
}
