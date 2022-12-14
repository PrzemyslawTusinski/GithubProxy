package interview.project.Github;

import interview.project.Github.data.GithubBranch;
import interview.project.Github.data.GithubRepository;
import interview.project.Github.data.ProxyBranch;
import interview.project.Github.data.ProxyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


@Service
public class GithubProxyService {
    final private String userReposUrl = "https://api.github.com";

    @Value("${use_personal_access_key}")
    private Boolean usePersonalAccessKey;
    @Value("${github.personal.access.key}")
    private String personalAccessKey;
    final private RestTemplate restTemplate;

    GithubProxyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ProxyResponse> getReposFor(String username) throws GithubProxyUserNotFoundException {
        List<ProxyResponse> response = getUserProjectsFromGithub(username);
        fillBranchesForRepositoriesFromGithub(username, response);

        return response;
    }

    private List<ProxyResponse> getUserProjectsFromGithub(String username) {
        String url = userReposUrl + "/users/" + username + "/repos";

        List<ProxyResponse> retVal = new ArrayList<>();

        GithubRepository[] repositories;
        try {
            repositories = restTemplate.exchange(url, HttpMethod.GET, getAuthorizationTokenEntity(), GithubRepository[].class).getBody();
        } catch (HttpClientErrorException ex) {
            if(ex.getStatusCode() == HttpStatus.NOT_FOUND)
                throw new GithubProxyUserNotFoundException();
             if(ex.getStatusCode() == HttpStatus.FORBIDDEN)
                 throw new GithubProxyLimitExceeded();
            throw ex;
        }

        for (GithubRepository repository : Objects.requireNonNull(repositories)) {
            if(!repository.isFork()) {
                retVal.add(new ProxyResponse(repository.getName(), repository.getOwner().getLogin()));
            }
        }

        return retVal;
    }

    private void fillBranchesForRepositoriesFromGithub(String username, List<ProxyResponse> response) {
        response.parallelStream().forEach(responsePOJO -> {
            String branchesUrl = userReposUrl + "/repos/" + username + "/" + responsePOJO.getRepositoryName() + "/branches";

            GithubBranch[] branches;
            try {
                branches = restTemplate.exchange(branchesUrl, HttpMethod.GET, getAuthorizationTokenEntity(), GithubBranch[].class).getBody();
            } catch (HttpClientErrorException ex) {
                if(ex.getStatusCode() == HttpStatus.FORBIDDEN)
                    throw new GithubProxyLimitExceeded();
                throw ex;
            }

            responsePOJO.setBranches(Arrays.stream(branches)
                    .map(externalBranch -> new ProxyBranch(externalBranch.getName(), externalBranch.getCommit().getSha()))
                    .toList());
        });
    }

    private HttpEntity<String> getAuthorizationTokenEntity() {
        HttpHeaders headers = new HttpHeaders();

        if(usePersonalAccessKey) {
            headers.set("Authorization", "Bearer " + personalAccessKey);
        }

        HttpEntity <String> entity = new HttpEntity <> ("", headers);

        return entity;
    }
}