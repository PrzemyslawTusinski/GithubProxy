package interview.project.Github;

import interview.project.Github.data.GithubRepository;
import interview.project.Github.data.ProxyRepositories;
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


@Service
@AllArgsConstructor
public class GithubProxyRepositoryService {
    private final RestTemplate restTemplate;

    private final GithubProxyBranchService githubProxyBranchService;

    private final HttpEntity<String> authorizationTokenHttpEntity;

    public List<ProxyRepositories> listRepositories(String username) throws GithubProxyUserNotFoundException {
        final String url = String.format("https://api.github.com/users/%s/repos", username);

        try {
            GithubRepository[] repositories = restTemplate.exchange(url, HttpMethod.GET, authorizationTokenHttpEntity, GithubRepository[].class).getBody();

            return Arrays.stream(Objects.requireNonNull(repositories)).parallel()
                    .filter(repository -> !repository.isFork())
                    .map(repository -> new ProxyRepositories(
                            repository.getName(),
                            repository.getOwner().getLogin(),
                            githubProxyBranchService.listBranchesForRepository(username, repository.getName())))
                    .toList();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND)
                throw new GithubProxyUserNotFoundException();
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN)
                throw new GithubProxyLimitExceeded();
            throw ex;
        }
    }
}