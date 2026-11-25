package org.codeberg.edryal.ledger.bookkeeping.web;

import java.net.URI;

import org.codeberg.edryal.ledger.bookkeeping.AccountNotFoundException;
import org.codeberg.edryal.ledger.bookkeeping.CurrencyMismatchException;
import org.codeberg.edryal.ledger.bookkeeping.DuplicateReferenceException;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryNotFoundException;
import org.codeberg.edryal.ledger.bookkeeping.UnsupportedCurrencyException;
import org.codeberg.edryal.ledger.bookkeeping.application.InvalidCursorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns bookkeeping exceptions into RFC 9457 Problem Details responses. */
@RestControllerAdvice
class BookkeepingExceptionHandler {

    @ExceptionHandler
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Account not found", e);
    }

    @ExceptionHandler
    ProblemDetail duplicateReference(DuplicateReferenceException e) {
        return problem(HttpStatus.CONFLICT, "duplicate-reference", "Duplicate reference", e);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setType(URI.create("/problems/" + type));
        problem.setTitle(title);
        return problem;
    }

    @ExceptionHandler
    ProblemDetail journalEntryNotFound(JournalEntryNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Journal entry not found", e);
    }

    @ExceptionHandler
    ProblemDetail invalidCursor(InvalidCursorException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-cursor", "Invalid cursor", e);
    }

    @ExceptionHandler
    ProblemDetail unsupportedCurrency(UnsupportedCurrencyException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "unsupported-currency", "Unsupported currency", e);
    }

    @ExceptionHandler
    ProblemDetail currencyMismatch(CurrencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "currency-mismatch", "Currency mismatch", e);
    }
}
