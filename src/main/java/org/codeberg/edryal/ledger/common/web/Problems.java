package org.codeberg.edryal.ledger.common.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 Problem Details responses with this API's problem types. */
public final class Problems {

    private Problems() {
    }

    /**
     * A problem response whose detail is the exception's message.
     *
     * @param type the problem type, e.g. {@code "not-found"}, becomes {@code /problems/not-found}
     */
    public static ProblemDetail of(HttpStatus status, String type, String title, Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setType(URI.create("/problems/" + type));
        problem.setTitle(title);
        return problem;
    }
}
