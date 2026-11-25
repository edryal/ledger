package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Currency;

/** Thrown when a currency has no system accounts, so the platform can't hold money in it. */
public class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(Currency currency) {
        super("currency not supported: " + currency.getCurrencyCode());
    }
}
