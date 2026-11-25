package org.codeberg.edryal.ledger.transfers;

enum TransferKind {
    /** Money comes in from outside the platform (e.g. a bank transfer). */
    DEPOSIT,

    /** Money leaves the platform. */
    WITHDRAWAL,

    /** Money moves between two accounts on the platform. */
    TRANSFER
}
