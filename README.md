# GitHub Proxy

A Spring Boot service in front of the GitHub REST API. For a GitHub user it lists every repository that is not a
fork, with its owner login and the name and last commit SHA of each branch.

## Build and run

You need JDK 17 or newer (the Maven wrapper is included) or Docker.

```bash
./mvnw verify                       # tests run against WireMock, never api.github.com (Windows: mvnw.cmd verify)
java -jar target/github-proxy.jar   # serves http://localhost:8080
```

```bash
docker build -t github-proxy .      # skips the tests, so run ./mvnw verify first
docker run --rm -p 8080:8080 -e GITHUB_TOKEN=<your token> github-proxy
```

## Configuration

Set these as environment variables or as Spring properties (for example `--github.token=...`).

| Property                                     | Environment variable                         | Default                  | Meaning                                         |
|----------------------------------------------|----------------------------------------------|--------------------------|-------------------------------------------------|
| `github.base-url`                            | `GITHUB_BASE_URL`                            | `https://api.github.com` | GitHub API location                             |
| `github.token`                               | `GITHUB_TOKEN`                               | empty                    | Sent as `Authorization: Bearer <token>` if set  |
| `spring.http.reactiveclient.connect-timeout` | `SPRING_HTTP_REACTIVECLIENT_CONNECT_TIMEOUT` | `5s`                     | Connect timeout for GitHub calls                |
| `spring.http.reactiveclient.read-timeout`    | `SPRING_HTTP_REACTIVECLIENT_READ_TIMEOUT`    | `10s`                    | Read timeout for GitHub calls                   |

Without a token GitHub allows 60 requests per hour. One listing takes one request per page of repositories plus one
per page of branches of each non-fork repository.

## API

### `GET /github_proxy/v1/repositories/{username}`

```bash
curl -H "Accept: application/json" http://localhost:8080/github_proxy/v1/repositories/octocat
```

```json
[
  {
    "repositoryName": "Hello-World",
    "ownerLogin": "octocat",
    "branches": [
      { "name": "master", "lastCommitSha": "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d" }
    ]
  }
]
```

Repositories come in the order GitHub returns them.

### Errors

Errors of the endpoint have the body `{"status": <code>, "Message": "<reason>"}` with `Content-Type: application/json`.
Requests Tomcat rejects before they reach Spring, such as a malformed URL, get Tomcat's default error page.

| Status | When                                                                                     |
|--------|------------------------------------------------------------------------------------------|
| 404    | The GitHub user does not exist, or no endpoint matches the path                          |
| 405    | The endpoint is called with a method other than GET, HEAD or OPTIONS, for example POST   |
| 406    | `Accept` does not allow `application/json`, for example `application/xml`                |
| 500    | Unexpected error inside the proxy                                                        |
| 502    | GitHub returned another error or an unusable response, or could not be reached in time   |
| 503    | GitHub rate limit exceeded (429, or 403 with `X-RateLimit-Remaining: 0`); set a token    |

### Swagger

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Design notes

- Branches of different repositories are fetched in parallel on a non-blocking `WebClient`, at most 10 at a time.
  The response still keeps GitHub's repository order.
- Both GitHub endpoints are paginated. The client follows the `Link: <...>; rel="next"` header, but only takes the
  link's path and query and sends them to `github.base-url`, so the token never goes to another host.
- Fail fast: if any GitHub call fails, the whole request fails with that error. There are no partial results.
- No cache and no retries, on purpose: every response shows GitHub's current data, and callers can retry a 502 or
  503 themselves.

## Task

The original task statement, verbatim:

```text
Acceptance criteria:
1. As an api consumer, given username and header “Accept: application/json”, I would like to list all his github repositories, which are not forks. Information, which I require in the response, is:
Repository Name
Owner Login
For each branch it’s name and last commit sha

2. As an api consumer, given not existing github user, I would like to receive 404 response in such a format:
{
“status”: ${responseCode}
“Message”: ${whyHasItHappened}
}

3. As an api consumer, given header “Accept: application/xml”, I would like to receive 406 response in such a format:
{
“status”: ${responseCode}
“Message”: ${whyHasItHappened}
}

Notes:
- Please full-fill the given acceptance criteria, delivering us your best code compliant with industry standards.
- Please use https://developer.github.com/v3 as a backing API
- Please expose swagger
- Application should have a proper README.md file
- Add unit and integration test cases.
- Prepare Dockerfile so that app can be run in a container
- Make sure that data is downloaded in parallel, not in sequence
- U can either use reactive stack of Your choice, or stick to non-reactive/blocking approach

Technologies to use (required):
- Java
- Maven
- Spring Boot
- 
```
