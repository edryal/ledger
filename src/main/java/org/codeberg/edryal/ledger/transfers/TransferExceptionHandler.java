package org.codeberg.edryal.ledger.transfers;

import org.codeberg.edryal.ledger.common.web.Problems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns transfer exceptions into RFC 9457 Problem Details responses. */
@RestControllerAdvice
class TransferExceptionHandler {

    @ExceptionHandler
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds", "Insufficient funds", e);
    }

    @ExceptionHandler
    ProblemDetail sameAccount(SameAccountException e) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "same-account", "Same account", e);
    }

    @ExceptionHandler
    ProblemDetail transferNotFound(TransferNotFoundException e) {
        return Problems.of(HttpStatus.NOT_FOUND, "not-found", "Transfer not found", e);
    }
}
