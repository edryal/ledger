package org.codeberg.edryal.ledger.bookkeeping.application;

/** Thrown when a client sends a cursor that wasn't produced by this API. */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("invalid cursor: " + cursor);
    }
}
