package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId parse(String text) {
        return new AccountId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
