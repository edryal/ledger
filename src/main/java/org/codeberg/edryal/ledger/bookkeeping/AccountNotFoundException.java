package org.codeberg.edryal.ledger.bookkeeping;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(AccountId id) {
        super("account not found: " + id);
    }
}
