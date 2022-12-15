package interview.project.Github;

import com.google.gson.Gson;
import interview.project.Github.data.GithubBranch;
import interview.project.Github.data.GithubCommit;
import interview.project.Github.data.GithubOwner;
import interview.project.Github.data.GithubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static java.util.Arrays.asList;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest()
@AutoConfigureMockMvc
public class GithubProxyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RestTemplate restTemplate;

	private MockRestServiceServer mockServer;

	private final String USER1 = "user1";
	private final String USER3 = "user3";
	private final String REPO1 = "repo1";
	private final String REPO2 = "repo2";
	private final String REPO3 = "repo3";
	private final String REPO4 = "repo4";

	private final String BRANCH1 = "branch1";
	private final String SHA1 = "sha1";
	private final String BRANCH2 = "branch2";
	private final String SHA2 = "sha2";

	@BeforeEach
	public void init() {
		mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
	}

	@Test
	public void testApi_GivenXmlHeader_ShouldReturnNotAcceptable() throws Exception {
		this.mockMvc.perform(get("http://localhost:8080/github_proxy/v1/repositories/user1")
						.header("accept", "application/xml"))
				.andExpect(status().is(HttpStatus.NOT_ACCEPTABLE.value()))
				.andExpect(content().json("{\"errorCode\":403,\"message\":\"XML is not supported\"}"));
	}

	@Test
	public void testApi_GivenNonExistingUser_ShouldReturnNotFound() throws Exception {
		mockServer.expect(ExpectedCount.once(),
						requestTo(new URI("https://api.github.com/users/user2/repos")))
				.andExpect(method(HttpMethod.GET)).
				andRespond((response) -> { throw new HttpClientErrorException(HttpStatus.NOT_FOUND);});

		this.mockMvc.perform(get("http://localhost:8080/github_proxy/v1/repositories/user2")
						.header("accept", "application/json"))
				.andExpect(status().is(HttpStatus.NOT_FOUND.value()))
				.andExpect(content().json("{\"errorCode\":404,\"message\":\"User not found\"}"));
	}

	@Test
	public void testApi_GivenUserWith4Repos2ForksAnd2NonForks_ShouldReturnNonForksOnly() throws Exception {

		mockServer.expect(ExpectedCount.once(),
						requestTo(new URI("https://api.github.com/users/user3/repos")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(getMockedRepositoryResponse(), MediaType.APPLICATION_JSON));

		mockServer.expect(requestTo(new URI("https://api.github.com/repos/user3/repo2/branches")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(getTwoMockedBranchesResponse(), MediaType.APPLICATION_JSON));
		mockServer.expect(requestTo(new URI("https://api.github.com/repos/user3/repo4/branches")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(getTwoMockedBranchesResponse(), MediaType.APPLICATION_JSON));

		mockMvc.perform(get("http://localhost:8080/github_proxy/v1/repositories/user3")
						.header("accept", "application/json"))
				.andExpect(status().is(HttpStatus.OK.value()))
				.andExpect(content().json("[{\"repositoryName\":\"repo2\",\"ownerLogon\":\"user3\",\"branches\":[{\"name\":\"branch1\",\"lastCommitSha\":\"sha1\"},{\"name\":\"branch2\",\"lastCommitSha\":\"sha2\"}]},{\"repositoryName\":\"repo4\",\"ownerLogon\":\"user3\",\"branches\":[{\"name\":\"branch1\",\"lastCommitSha\":\"sha1\"},{\"name\":\"branch2\",\"lastCommitSha\":\"sha2\"}]}]"));
		//this could be done better...
	}

	public String getTwoMockedBranchesResponse() {
		GithubBranch branch1 = GithubBranch.builder()
				.name(BRANCH1)
				.commit(GithubCommit.builder().sha(SHA1).build())
				.build();

		GithubBranch branch2 = GithubBranch.builder()
				.name(BRANCH2)
				.commit(GithubCommit.builder().sha(SHA2).build())
				.build();

		List<GithubBranch> repos = asList(branch1, branch2);

		return new Gson().toJson(repos);
	}
	public String getMockedRepositoryResponse() {
		GithubRepository repository1 = GithubRepository.builder()
				.name(REPO1)
				.fork(true)
				.owner(GithubOwner.builder().login(USER3).build())
				.build();
		GithubRepository repository2 = GithubRepository.builder()
				.name(REPO2)
				.fork(false)
				.owner(GithubOwner.builder().login(USER3).build())
				.build();
		GithubRepository repository3 = GithubRepository.builder()
				.name(REPO3)
				.fork(true)
				.owner(GithubOwner.builder().login(USER3).build())
				.build();
		GithubRepository repository4 = GithubRepository.builder()
				.name(REPO4)
				.fork(false)
				.owner(GithubOwner.builder().login(USER3).build())
				.build();

		List<GithubRepository> repos = asList(repository1, repository2, repository3, repository4);

		return new Gson().toJson(repos);
	}
}