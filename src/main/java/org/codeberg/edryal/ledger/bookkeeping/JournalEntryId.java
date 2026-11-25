package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Objects;
import java.util.UUID;

public record JournalEntryId(UUID value) {

    public JournalEntryId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static JournalEntryId newId() {
        return new JournalEntryId(UUID.randomUUID());
    }

    public static JournalEntryId parse(String text) {
        return new JournalEntryId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
