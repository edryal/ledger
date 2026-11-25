package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to open a new account.
 *
 * @param type     what the account is for
 * @param currency the only currency the account can hold
 * @param ownerId  the customer or merchant who owns the account
 */
public record OpenAccount(AccountType type, Currency currency, UUID ownerId) {

    public OpenAccount {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
    }
}
