package org.codeberg.edryal.ledger.transfers;

import java.util.Objects;
import java.util.UUID;

public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TransferId newId() {
        return new TransferId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
