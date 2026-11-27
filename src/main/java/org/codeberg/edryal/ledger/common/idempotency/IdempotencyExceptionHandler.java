package org.codeberg.edryal.ledger.common.idempotency;

import org.codeberg.edryal.ledger.common.web.Problems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns idempotency exceptions into RFC 9457 Problem Details responses. */
@RestControllerAdvice
class IdempotencyExceptionHandler {

    @ExceptionHandler
    ProblemDetail missingKey(MissingIdempotencyKeyException e) {
        return Problems.of(HttpStatus.BAD_REQUEST, "missing-idempotency-key", "Missing idempotency key", e);
    }

    @ExceptionHandler
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", "Idempotency key reused", e);
    }
}
