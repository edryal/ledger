package org.codeberg.edryal.ledger.bookkeeping.domain;

import org.codeberg.edryal.ledger.bookkeeping.Direction;

/**
 * The accounting category of an account, which decides its normal side.
 *
 * <p>Postings on the normal side increase the balance, postings on the other side decrease it.
 * Customer wallets are liabilities because the platform owes that money to the customer.
 */
public enum AccountCategory {

    ASSET(Direction.DEBIT),
    LIABILITY(Direction.CREDIT),
    REVENUE(Direction.CREDIT);

    private final Direction normalSide;

    AccountCategory(Direction normalSide) {
        this.normalSide = normalSide;
    }

    /** The side that increases accounts of this category: debit for assets, credit otherwise. */
    public Direction normalSide() {
        return normalSide;
    }
}
