package interview.project.Github;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.swagger2.annotations.EnableSwagger2;


@EnableSwagger2
@Api(value = "Github Proxy Controller")
@RestController
@RequestMapping("/github_proxy/v1")
@AllArgsConstructor
public class GithubProxyController {
    private final GithubProxyRepositoryService githubService;

    @ApiOperation(value = "List repositories for a given username")
    @GetMapping(path = "/repositories/{username}")
    public ResponseEntity<?> listRepositories(@PathVariable String username, @RequestHeader(HttpHeaders.ACCEPT) String acceptHeader) {
        if (acceptHeader.equals("application/xml")) {
            throw new GithubProxyXmlNotSupported();
        }

        var repositories = githubService.listRepositories(username);

        return ResponseEntity.ok(repositories);
    }
}