package interview.project.github.service;

import interview.project.github.client.GithubBranch;
import interview.project.github.client.GithubClient;
import interview.project.github.client.GithubRepository;
import interview.project.github.exception.UserNotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link GithubClient} that records branch requests and how many of them ran at the same time.
 */
class FakeGithubClient implements GithubClient {

    private final Map<String, List<GithubRepository>> repositoriesByUser = new HashMap<>();
    private final Map<String, List<GithubBranch>> branchesByRepository = new HashMap<>();
    private final Map<String, Duration> branchDelays = new HashMap<>();
    private final Map<String, Throwable> branchErrors = new HashMap<>();
    private final List<String> branchRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger branchRequestsInFlight = new AtomicInteger();
    private final AtomicInteger maxBranchRequestsInFlight = new AtomicInteger();

    void addRepository(String username, GithubRepository repository) {
        repositoriesByUser.computeIfAbsent(username, key -> new ArrayList<>()).add(repository);
    }

    void addUserWithoutRepositories(String username) {
        repositoriesByUser.put(username, new ArrayList<>());
    }

    void addBranches(String owner, String repository, List<GithubBranch> branches) {
        branchesByRepository.put(key(owner, repository), branches);
    }

    void delayBranches(String owner, String repository, Duration delay) {
        branchDelays.put(key(owner, repository), delay);
    }

    void failBranches(String owner, String repository, Throwable error) {
        branchErrors.put(key(owner, repository), error);
    }

    List<String> branchRequests() {
        return branchRequests;
    }

    int maxBranchRequestsInFlight() {
        return maxBranchRequestsInFlight.get();
    }

    @Override
    public Flux<GithubRepository> listUserRepositories(String username) {
        List<GithubRepository> repositories = repositoriesByUser.get(username);
        return repositories == null
                ? Flux.error(new UserNotFoundException(username))
                : Flux.fromIterable(repositories);
    }

    @Override
    public Flux<GithubBranch> listBranches(String owner, String repository) {
        String key = key(owner, repository);
        return Flux.defer(() -> {
            branchRequests.add(key);
            maxBranchRequestsInFlight.accumulateAndGet(branchRequestsInFlight.incrementAndGet(), Math::max);
            Flux<GithubBranch> branches = branchErrors.containsKey(key)
                    ? Flux.error(branchErrors.get(key))
                    : Flux.fromIterable(branchesByRepository.getOrDefault(key, List.of()));
            return Mono.delay(branchDelays.getOrDefault(key, Duration.ZERO))
                    .thenMany(branches)
                    // doOnTerminate runs before completion reaches the subscriber, so before it starts the next request
                    .doOnTerminate(branchRequestsInFlight::decrementAndGet)
                    .doOnCancel(branchRequestsInFlight::decrementAndGet);
        });
    }

    private static String key(String owner, String repository) {
        return owner + "/" + repository;
    }
}
