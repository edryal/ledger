package org.codeberg.edryal.ledger.transfers;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;

/** Thrown when a transfer's source and destination are the same account. */
class SameAccountException extends RuntimeException {

    SameAccountException(AccountId account) {
        super("cannot transfer from account %s to itself".formatted(account));
    }
}
