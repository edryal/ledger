package org.codeberg.edryal.ledger.transfers;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.common.Money;

/** Thrown when an account doesn't have enough money. Rolls back the whole request. */
class InsufficientFundsException extends RuntimeException {

    InsufficientFundsException(AccountId account, Money available, Money required) {
        super("account %s has %s available, %s required".formatted(account, available, required));
    }
}
