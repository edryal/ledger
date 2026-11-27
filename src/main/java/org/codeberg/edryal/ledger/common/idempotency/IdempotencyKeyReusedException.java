package org.codeberg.edryal.ledger.common.idempotency;

/** Thrown when a key is sent again with a different request. */
public class IdempotencyKeyReusedException extends RuntimeException {

    IdempotencyKeyReusedException(String key) {
        super("idempotency key '" + key + "' was already used for a different request");
    }
}
