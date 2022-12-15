package interview.project.Github;

import interview.project.Github.data.ProxyErrorResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GithubProxyExceptionHandler {
    @ExceptionHandler(GithubProxyUserNotFoundException.class)
    private ResponseEntity<?> handleGithubProxyUserNotFoundException(GithubProxyUserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ProxyErrorResult(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(GithubProxyLimitExceeded.class)
    private ResponseEntity<?> handleGithubProxyLimitExceeded(GithubProxyLimitExceeded ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ProxyErrorResult(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(GithubProxyXmlNotSupported.class)
    private ResponseEntity<?> handleGithubProxyXmlNotSupported(GithubProxyXmlNotSupported ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProxyErrorResult(HttpStatus.FORBIDDEN.value(), "XML is not supported"));
    }
}
