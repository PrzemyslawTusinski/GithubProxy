package interview.project.Github;

import interview.project.Github.data.GithubBranch;
import interview.project.Github.data.ProxyBranch;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GithubProxyBranchService {
    private final RestTemplate restTemplate;

    private final HttpEntity<String> authorizationTokenHttpEntity;

    public List<ProxyBranch> listBranchesForRepository(String username, String repositoryName) {
        final String url = String.format("https://api.github.com/repos/%s/%s/branches", username, repositoryName);

        try {
            GithubBranch[] branches = restTemplate.exchange(url, HttpMethod.GET, authorizationTokenHttpEntity, GithubBranch[].class).getBody();

            return Arrays.stream(Objects.requireNonNull(branches))
                    .map(externalBranch -> new ProxyBranch(externalBranch.getName(), externalBranch.getCommit().getSha()))
                    .collect(Collectors.toList());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN)
                throw new GithubProxyLimitExceeded();
            throw ex;
        }
    }
}
