package org.codeberg.edryal.ledger.common.idempotency;

/** Thrown when a request that moves money has no valid {@code Idempotency-Key} header. */
public class MissingIdempotencyKeyException extends RuntimeException {

    MissingIdempotencyKeyException() {
        super("the Idempotency-Key header is required and must be 1 to 255 characters");
    }
}
