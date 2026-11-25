package org.codeberg.edryal.ledger.bookkeeping;

/** The kinds of accounts other modules can open. System accounts are created by migrations. */
public enum AccountType {
    CUSTOMER_WALLET,
    MERCHANT_BALANCE
}
