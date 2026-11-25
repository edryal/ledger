package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Currency;

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

    /**
     * The ID of a system account.
     *
     * @throws IllegalArgumentException if there is no such account for this currency
     */
    AccountId systemAccount(SystemAccount account, Currency currency);

    /**
     * Posts a journal entry and updates the balances of all accounts involved, atomically.
     *
     * <p>Returns {@link PostResult.Rejected} without writing anything if an account would go below zero.
     *
     * @throws AccountNotFoundException    if a posting refers to an unknown account
     * @throws DuplicateReferenceException if an entry with the same reference already exists
     * @throws IllegalArgumentException    if the entry is invalid (unbalanced, wrong currency, ...)
     */
    PostResult post(PostEntry command);
}
