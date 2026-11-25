package org.codeberg.edryal.ledger.bookkeeping;

import java.util.Currency;

/** The platform's own accounts. One of each exists per supported currency. */
public enum SystemAccount {

    /** Money held at the partner bank (asset). */
    CASH("cash"),

    /** Money reserved by payment authorizations (liability). */
    HOLDS("holds"),

    /** Fees earned by the platform (revenue). */
    FEES("fees");

    private final String name;

    SystemAccount(String name) {
        this.name = name;
    }

    /** The account code in the database, e.g. {@code platform.cash.EUR}. */
    public String code(Currency currency) {
        return "platform." + name + "." + currency.getCurrencyCode();
    }
}
