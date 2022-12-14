package interview.project.Github;

import interview.project.Github.data.ProxyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GithubProxyControllerTest {
	private GithubProxyController controller;
	private GithubProxyService service;

	@BeforeEach
	public void setup() {
		service = mock(GithubProxyService.class);
		controller = new GithubProxyController(service);
	}

	@Test
	public void testListRepositories_GivenAcceptHeaderIsXml_ReturnsNotAcceptable() {
		// arrange
		String username = "user1";
		String acceptHeader = "application/xml";

		// act
		ResponseEntity<?> response = controller.listRepositories(username, acceptHeader);

		// assert
		assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
		assertEquals("XML is not supported", response.getBody());
	}

	@Test
	public void testListRepositories_GivenAcceptHeaderIsJson_ReturnsOk() {
		// arrange
		String username = "user1";
		String acceptHeader = "application/json";
		List<ProxyResponse> expectedRepositories = Arrays.asList(new ProxyResponse("repo1", "user1"), new ProxyResponse("repo2", "user1"));
		when(service.getReposFor(username)).thenReturn(expectedRepositories);

		// act
		ResponseEntity<?> response = controller.listRepositories(username, acceptHeader);

		// assert
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(expectedRepositories, response.getBody());
	}

	@Test
	public void testHandleGithubProxyUserNotFoundException_ReturnsNotFound() {
		// Act
//		ResponseEntity<?> response = controller.handleGithubProxyUserNotFoundException(new GithubProxyUserNotFoundException());
//
//		// Assert
//		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}
}
