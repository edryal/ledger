package org.codeberg.edryal.ledger.common.idempotency;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns idempotency exceptions into RFC 9457 Problem Details responses. */
@RestControllerAdvice
class IdempotencyExceptionHandler {

    @ExceptionHandler
    ProblemDetail missingKey(MissingIdempotencyKeyException e) {
        return problem(HttpStatus.BAD_REQUEST, "missing-idempotency-key", "Missing idempotency key", e);
    }

    @ExceptionHandler
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", "Idempotency key reused", e);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setType(URI.create("/problems/" + type));
        problem.setTitle(title);
        return problem;
    }
}
