package org.codeberg.edryal.ledger.bookkeeping;

import org.codeberg.edryal.ledger.common.Money;

/** The public API of the bookkeeping module. Other modules use this, never the internals. */
public interface Ledger {

    /**
     * Opens an account with a zero balance.
     *
     * @return the ID of the new account
     */
    AccountId open(OpenAccount command);

    /**
     * The current balance of an account, oriented to its normal side.
     *
     * @throws AccountNotFoundException if the account doesn't exist
     */
    Money balanceOf(AccountId id);
}
