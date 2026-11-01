# GitHub Proxy

A small Spring Boot service in front of the GitHub REST API. For a given GitHub user it lists every repository
that is not a fork, with the owner login and, for each branch, the branch name and the SHA of its last commit.
Errors are returned in one JSON shape: `{"status": <code>, "Message": "<reason>"}`.

## Requirements

- JDK 17 or newer (the Maven wrapper is included, no local Maven needed), or
- Docker

## Build, test and run

```bash
./mvnw test                         # unit and integration tests (Windows: mvnw.cmd test)
./mvnw package                      # builds target/github-proxy.jar (runs the tests too)
java -jar target/github-proxy.jar   # starts the app on http://localhost:8080
```

The tests never call api.github.com: integration tests run the whole application against a WireMock server.

## Docker

```bash
docker build -t github-proxy .
docker run --rm -p 8080:8080 -e GITHUB_TOKEN=<your token> github-proxy
```

The image build skips the tests (`-DskipTests`); they are meant to run in CI with `./mvnw verify`.
The container runs as a non-root user and has a `HEALTHCHECK` on `/actuator/health`.

## Configuration

Every setting can be passed as an environment variable or as a Spring property
(for example `java -jar target/github-proxy.jar --github.read-timeout=5s`).

| Property                 | Environment variable     | Default                  | Description                                                     |
|--------------------------|--------------------------|--------------------------|-----------------------------------------------------------------|
| `github.token`           | `GITHUB_TOKEN`           | empty                    | Personal access token, sent as `Authorization: Bearer <token>`  |
| `github.api.base-url`    | `GITHUB_API_BASE_URL`    | `https://api.github.com` | GitHub API location                                             |
| `github.connect-timeout` | `GITHUB_CONNECT_TIMEOUT` | `5s`                     | TCP connect timeout                                             |
| `github.read-timeout`    | `GITHUB_READ_TIMEOUT`    | `10s`                    | Maximum wait for a GitHub response                              |
| `github.page-size`       | `GITHUB_PAGE_SIZE`       | `100`                    | Items requested per page (`per_page`, 1-100)                    |
| `github.max-concurrency` | `GITHUB_MAX_CONCURRENCY` | `10`                     | Maximum number of branch requests sent to GitHub at once        |

Without a token GitHub allows 60 requests per hour per IP address, and this service makes one request per page of
repositories plus one per page of branches of every non-fork repository. A token raises the limit considerably; see
[Managing your personal access tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens).

## API

### `GET /github_proxy/v1/repositories/{username}`

```bash
curl -H "Accept: application/json" http://localhost:8080/github_proxy/v1/repositories/octocat
```

Example `200 OK` response (shortened):

```json
[
  {
    "repositoryName": "Hello-World",
    "ownerLogin": "octocat",
    "branches": [
      { "name": "master", "lastCommitSha": "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d" },
      { "name": "test", "lastCommitSha": "b3cbd5bbd7e81436d2eee04537ea2b4c0cad4cdf" }
    ]
  }
]
```

Repositories are returned in the order GitHub returns them.

### Errors

All errors use the same body, with `Content-Type: application/json`:

```json
{ "status": 404, "Message": "GitHub user 'no-such-user' not found" }
```

| Status | When                                                                                                    |
|--------|---------------------------------------------------------------------------------------------------------|
| 400    | `username` is not a valid GitHub login (1-39 letters, digits or single hyphens, no leading/trailing hyphen) |
| 404    | The GitHub user does not exist, or the path does not match any endpoint                                 |
| 406    | The `Accept` header does not allow `application/json` (for example `application/xml`)                   |
| 502    | GitHub answered with an unexpected status (5xx, 401, a 403 that is not a rate limit, ...), an empty or invalid body, or could not be reached |
| 503    | GitHub rate limit exceeded (HTTP 429, or 403 with `X-RateLimit-Remaining: 0`); `Retry-After` is set when GitHub tells when the limit resets |
| 504    | GitHub did not respond within `github.read-timeout`                                                     |
| 500    | Unexpected error inside the proxy                                                                       |

### Documentation and health

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

## Design notes

- **Parallel branch fetching.** GitHub has no endpoint returning repositories together with their branches, so the
  service makes one call per repository. These calls run concurrently on a non-blocking `WebClient`
  (`flatMapSequential`, at most `github.max-concurrency` at a time), and the results keep GitHub's repository order.
  The web layer stays on Spring MVC; the reactive pipeline is blocked on once, at the service boundary.
- **Pagination.** Both GitHub endpoints return 30 items per page by default. The client asks for
  `per_page=100` and follows the `Link: <...>; rel="next"` header until the last page. Links are only followed to
  the configured GitHub host, because the token is sent with every request.
- **Rate limits.** HTTP 429 and HTTP 403 with `X-RateLimit-Remaining: 0` are reported as `503 Service Unavailable`,
  with `Retry-After` taken from GitHub's `Retry-After` or `X-RateLimit-Reset` header. Set `GITHUB_TOKEN` to get a
  much higher limit.
- **Timeouts.** The HTTP client has explicit connect and read timeouts, so a slow GitHub cannot hold request threads
  indefinitely.

## Task

The original acceptance criteria this service implements:

1. As an API consumer, given a username and the header `Accept: application/json`, I would like to list all their
   GitHub repositories that are not forks. The response must contain: repository name, owner login, and for each
   branch its name and last commit SHA.
2. As an API consumer, given a GitHub user that does not exist, I would like to receive a 404 response in the format
   `{"status": ${responseCode}, "Message": ${whyHasItHappened}}`.
3. As an API consumer, given the header `Accept: application/xml`, I would like to receive a 406 response in the same
   format.

Notes: use https://developer.github.com/v3 as the backing API, expose Swagger, provide a proper README, add unit and
integration tests, prepare a Dockerfile, download the data in parallel rather than in sequence (reactive or blocking
stack allowed). Required technologies: Java, Maven, Spring Boot.
