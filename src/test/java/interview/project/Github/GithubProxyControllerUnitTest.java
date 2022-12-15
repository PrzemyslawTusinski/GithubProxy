package interview.project.Github;

import interview.project.Github.data.ProxyRepositories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
@SpringBootTest
public class GithubProxyControllerUnitTest {

	@MockBean
	private GithubProxyService service;

	@Autowired
	private GithubProxyController controller;

	private final String USER_1 = "user1";

	@Test
	public void testListRepositories_GivenAcceptHeaderIsXml_ReturnsNotAcceptable() {
		String XML_HEADER = "application/xml";
		ResponseEntity<?> response = controller.listRepositories(USER_1, XML_HEADER);

		assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
		assertEquals("{\"errorCode\":403,\"message\":\"XML is not supported\"}", response.getBody());
		// TODO make this better
	}

	@Test
	public void testListRepositories_GivenAcceptHeaderIsJson_ReturnsOk() {
		String JSON_HEADER = "application/json";

		List<ProxyRepositories> expectedRepositories = Arrays.asList(
				new ProxyRepositories("repo1", "user1"), new ProxyRepositories("repo2", "user1"));
		when(service.listRepositories(USER_1)).thenReturn(expectedRepositories);

		ResponseEntity<?> response = controller.listRepositories(USER_1, JSON_HEADER);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(expectedRepositories, response.getBody());
	}
}
