package org.codeberg.edryal.ledger.common.idempotency;

import java.util.Optional;

/** The request fingerprint and the response stored for an idempotency key. */
record StoredResponse(byte[] requestHash, int status, Optional<String> location, String body) {
}
