package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Currency;

/** Thrown when a posting's currency differs from its account's currency. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(AccountId account, Currency accountCurrency, Currency postingCurrency) {
        super("account %s holds %s, not %s".formatted(account, accountCurrency, postingCurrency));
    }
}
