package interview.project.github.service;

import interview.project.github.client.GithubBranch;
import interview.project.github.client.GithubClient;
import interview.project.github.client.GithubRepository;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FakeGithubClient implements GithubClient {

    private final Map<String, List<GithubRepository>> repositoriesByUser = new HashMap<>();
    private final Map<String, Flux<GithubBranch>> branchesByRepository = new HashMap<>();

    void addRepository(String username, GithubRepository repository) {
        repositoriesByUser.computeIfAbsent(username, key -> new ArrayList<>()).add(repository);
    }

    void addBranches(String owner, String repository, Flux<GithubBranch> branches) {
        branchesByRepository.put(owner + "/" + repository, branches);
    }

    @Override
    public Flux<GithubRepository> listUserRepositories(String username) {
        return Flux.fromIterable(repositoriesByUser.getOrDefault(username, List.of()));
    }

    @Override
    public Flux<GithubBranch> listBranches(String owner, String repository) {
        return branchesByRepository.getOrDefault(owner + "/" + repository, Flux.empty());
    }
}
