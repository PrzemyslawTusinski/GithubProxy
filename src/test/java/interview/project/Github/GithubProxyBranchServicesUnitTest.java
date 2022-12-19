package interview.project.Github;

import interview.project.Github.data.GithubBranch;
import interview.project.Github.data.GithubCommit;
import interview.project.Github.data.ProxyBranch;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GithubProxyBranchServicesUnitTest {

    @Mock
    private RestTemplate restTemplateMock;

    @Mock
    private HttpEntity<String> authorizationTokenHttpEntity;

    @InjectMocks
    private GithubProxyBranchService service;

    @Test()
    public void testListBranches_HttpStatusFORBIDDEN_ThrowsLimitExceeded() {
        when(restTemplateMock.exchange(
                "https://api.github.com/repos/user1/repo1/branches",
                HttpMethod.GET,
                authorizationTokenHttpEntity,
                GithubBranch[].class))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        Exception exception = assertThrows(GithubProxyLimitExceeded.class, () -> service.listBranchesForRepository("user1", "repo1"));

        assertEquals("Server exceeded limit request. Please try again soon or configure personal access token", exception.getMessage());
    }

    @SneakyThrows
    @Test()
    public void testListBranches_given2Branches_ShouldReturnThem() {
        GithubBranch[] branches = new GithubBranch[] {
                GithubBranch.builder().name("branch1")
                        .commit(new GithubCommit("sha1"))
                        .build(),
                GithubBranch.builder().name("branch2")
                        .commit(new GithubCommit("sha2"))
                        .build(),
        };

        when(restTemplateMock.exchange(
                "https://api.github.com/repos/user1/repo1/branches",
                HttpMethod.GET,
                authorizationTokenHttpEntity,
                GithubBranch[].class))
                .thenReturn(ResponseEntity.ok().body(branches));

        var expectedBranches = List.of(
                new ProxyBranch("branch1", "sha1"),
                new ProxyBranch("branch2", "sha2"));
        var actualBranches =  service.listBranchesForRepository("user1", "repo1");

        assertEquals(expectedBranches, actualBranches);
    }
}
