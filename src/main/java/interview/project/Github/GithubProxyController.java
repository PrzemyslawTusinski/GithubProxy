package interview.project.Github;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.swagger2.annotations.EnableSwagger2;


@EnableSwagger2
@Api(value = "Github Proxy Controller")
@RestController
@RequestMapping("/github_proxy/v1")
public class GithubProxyController {
    private final GithubProxyService githubService;

    GithubProxyController(GithubProxyService githubProxyService) {
        this.githubService = githubProxyService;
    }

    @ApiOperation(value = "List repositories for a given username")
    @GetMapping(path = "/repositories/{username}")
    public ResponseEntity<?> listRepositories(@PathVariable String username, @RequestHeader(HttpHeaders.ACCEPT) String acceptHeader) {
        if (acceptHeader.equals("application/xml")) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("XML is not supported");
        }

        var repositories = githubService.getReposFor(username);

        return ResponseEntity.ok(repositories);
    }

    @ExceptionHandler(GithubProxyUserNotFoundException.class)
    private ResponseEntity<?> handleGithubProxyUserNotFoundException(GithubProxyUserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(GithubProxyLimitExceeded.class)
    private ResponseEntity<?> handleGithubProxyLimitExceeded(GithubProxyLimitExceeded ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ex.getMessage());
    }
}