package org.codeberg.edryal.ledger.bookkeeping.domain;

import java.util.Currency;
import java.util.Objects;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.Posting;


/**
 * A ledger account holding money in exactly one currency.
 *
 * <p>The balance is not part of this record because it changes with every posting.
 * It is stored separately.
 */
public record Account(AccountId id, AccountCategory category, Currency currency, boolean allowNegative) {

    public Account {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }

    /**
     * How much a posting changes this account's balance, in minor units.
     *
     * <p>Positive if the posting is on the account's normal side, negative otherwise.
     * For example, a credit of 100.00 EUR to a customer wallet (a liability) returns {@code 10000},
     * a debit returns {@code -10000}.
     *
     * @throws IllegalArgumentException if the posting belongs to another account or uses another currency
     */
    public long balanceChange(Posting posting) {
        if (!posting.accountId().equals(id)) {
            throw new IllegalArgumentException("posting belongs to account " + posting.accountId() + ", not " + id);
        }

        if (!posting.amount().currency().equals(currency)) {
            throw new IllegalArgumentException("posting currency " + posting.amount().currency() +
                    " does not match account currency " + currency);
        }

        long amount = posting.amount().minorUnits();
        return posting.direction() == category.normalSide() ? amount : Math.negateExact(amount);
    }

    /** Whether this account may have the given balance. Negative balances need {@code allowNegative}. */
    public boolean permits(long balance) {
        return allowNegative || balance >= 0;
    }
}
