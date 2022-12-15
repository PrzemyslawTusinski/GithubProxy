package interview.project.Github;

import interview.project.Github.data.ProxyBranch;
import interview.project.Github.data.ProxyRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
public class GithubProxyControllerUnitTest {

	private GithubProxyRepositoryService serviceMock;

	private GithubProxyController controller;

	private final String USER_1 = "user1";

	@BeforeEach
	void init() {
		serviceMock = mock(GithubProxyRepositoryService.class);
		controller = new GithubProxyController(serviceMock);
	}

	@Test
	public void testListRepositories_GivenAcceptHeaderIsXml_ThrowsXmlNotSupported() {
		String XML_HEADER = "application/xml";

		assertThrows(GithubProxyXmlNotSupported.class, () -> controller.listRepositories(USER_1, XML_HEADER));
	}

	@Test
	public void testListRepositories_GivenAcceptHeaderIsJson_ReturnsOk() {
		String JSON_HEADER = "application/json";

		List<ProxyRepositories> expectedRepositories = Arrays.asList(
				new ProxyRepositories("repo1", "user1", List.of(new ProxyBranch("main", "sha1"))),
				new ProxyRepositories("repo2", "user1",  List.of(new ProxyBranch("main", "sha2"))));
		when(serviceMock.listRepositories(USER_1)).thenReturn(expectedRepositories);

		ResponseEntity<?> expectedResponse = ResponseEntity.ok().body(expectedRepositories);
		ResponseEntity<?> actualResponse = controller.listRepositories(USER_1, JSON_HEADER);

		assertThat(expectedResponse).isEqualTo(actualResponse);
	}
}
