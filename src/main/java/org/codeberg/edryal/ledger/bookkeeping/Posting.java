package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Objects;

import org.codeberg.edryal.ledger.common.Money;

/**
 * One line of a journal entry - an amount moved to the debit or credit side of an account.
 *
 * <p>The amount is always strictly positive. The {@link Direction} says which way the money
 * moves. Whether a posting increases or decreases a balance depends on the account,
 * see {@code Account.balanceChange}.
 */
public record Posting(AccountId accountId, Direction direction, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("posting amount must be positive: " + amount);
        }
    }

    public static Posting debit(AccountId accountId, Money amount) {
        return new Posting(accountId, Direction.DEBIT, amount);
    }

    public static Posting credit(AccountId accountId, Money amount) {
        return new Posting(accountId, Direction.CREDIT, amount);
    }

    // Debits count as +amount, credits as -amount. A balanced entry sums to zero.
    public long signedMinorUnits() {
        return direction == Direction.DEBIT ? amount.minorUnits() : Math.negateExact(amount.minorUnits());
    }
}
