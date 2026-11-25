package org.codeberg.edryal.ledger.bookkeeping.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Position in a statement: the last posting a client has seen.
 * Sent to clients as an opaque string, so they can't build or edit cursors themselves.
 */
public record Cursor(Instant postedAt, UUID postingId) {

    public String encode() {
        String raw = postedAt.toString() + "|" + postingId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidCursorException if the text isn't a cursor produced by {@link #encode()} */
    public static Cursor decode(String text) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(text);
        }
    }
}
