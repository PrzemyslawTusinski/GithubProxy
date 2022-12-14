package interview.project.Github;

import interview.project.Github.data.GithubRepository;
import interview.project.Github.data.ProxyResponse;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GithubProxyServiceTest {
    @Test
    public void testGetReposFor() {
        //mock the RestTemplate so it returns a known response
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(), any(), eq(GithubRepository[].class))).thenReturn(getMockedRepositoryResponse());
        GithubProxyService proxyService = new GithubProxyService(restTemplate);

        //call the getReposFor method with a username
        List<ProxyResponse> repos = proxyService.getReposFor("user1");

        //assert that the returned list is as expected
        assertEquals(2, repos.size());
        assertEquals("repo1", repos.get(0).getRepositoryName());
        assertEquals("user1", repos.get(0).getOwnerLogon());
        assertEquals("repo2", repos.get(1).getRepositoryName());
        assertEquals("user1", repos.get(1).getOwnerLogon());
    }

    private ResponseEntity<GithubRepository[]> getMockedRepositoryResponse() {
        return null;
    }

    //test that the getReposFor method throws a GithubProxyUserNotFoundException when the user is not found
    @Test(expected = GithubProxyUserNotFoundException.class)
    public void testGetReposForUserNotFound() {
        //mock the RestTemplate, so it throws a HttpClientErrorException with a NOT_FOUND status code
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(), any(), eq(GithubRepository[].class))).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        GithubProxyService proxyService = new GithubProxyService(restTemplate);

        //call the getReposFor method with a username
        proxyService.getReposFor("user1");
    }

    //test that the getReposFor method throws a GithubProxyLimitExceeded when the rate limit is exceeded
    @Test(expected = GithubProxyLimitExceeded.class)
    public void testGetReposForRateLimitExceeded() {
        //mock the RestTemplate, so it throws a HttpClientErrorException with a FORBIDDEN status code
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(), any(), eq(GithubRepository[].class))).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        GithubProxyService proxyService = new GithubProxyService(restTemplate);

        //call the getReposFor method with a username
        proxyService.getReposFor("user1");
    }

////helper method to return a mocked response for the RestTemplate's exchange method
//    private ResponseEntity<GithubRepository[]>

}