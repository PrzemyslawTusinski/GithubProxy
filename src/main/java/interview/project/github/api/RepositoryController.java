package interview.project.github.api;

import interview.project.github.service.RepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Repositories", description = "Non-fork GitHub repositories with their branches")
@RestController
@RequestMapping("/github_proxy/v1")
public class RepositoryController {

    /** GitHub login rules: 1-39 characters, alphanumerics and single hyphens, no leading or trailing hyphen. */
    static final String USERNAME_PATTERN = "(?=.{1,39}$)[A-Za-z0-9](?:-?[A-Za-z0-9])*";
    static final String INVALID_USERNAME_MESSAGE = "Invalid GitHub username: use 1-39 letters, digits or single "
            + "hyphens, not starting or ending with a hyphen";

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Operation(summary = "List non-fork repositories of a GitHub user",
            description = "Returns every repository of the user that is not a fork, with the name and last commit "
                    + "SHA of each branch. Only application/json is produced.")
    @ApiResponse(responseCode = "200", description = "Repositories of the user")
    @ApiResponse(responseCode = "400", description = "Invalid username", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "GitHub user does not exist", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested media type is not application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "GitHub API returned an error", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "GitHub API rate limit exceeded", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "504", description = "GitHub API did not respond in time", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(path = "/repositories/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RepositoryResponse> listRepositories(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable @Pattern(regexp = USERNAME_PATTERN, message = INVALID_USERNAME_MESSAGE) String username) {
        return repositoryService.listRepositories(username);
    }
}
