package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.exception.InvalidSubmissionException;
import gdgrvce.iudex.server.exception.ProblemNotFoundException;
import gdgrvce.iudex.server.exception.RateLimitExceededException;
import gdgrvce.iudex.server.exception.UsernameAlreadyExistsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns auth failures into the HTTP responses the client expects. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Tells the client when someone has already claimed the username. */
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUsernameTaken(UsernameAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /** Rejects bad credentials without revealing whether the username exists. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
    }

    /** Tells the client when a submission targets a problem that does not exist. */
    @ExceptionHandler(ProblemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProblemNotFound(ProblemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** Tells the client when a submission's shape is invalid, such as the wrong output count. */
    @ExceptionHandler(InvalidSubmissionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSubmission(InvalidSubmissionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /** Rejects submissions that arrive before the minimum interval has elapsed. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(Map.of("error", ex.getMessage()));
    }
}
