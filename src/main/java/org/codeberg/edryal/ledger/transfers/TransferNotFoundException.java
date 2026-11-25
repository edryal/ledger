package org.codeberg.edryal.ledger.transfers;

class TransferNotFoundException extends RuntimeException {

    TransferNotFoundException(TransferId id) {
        super("transfer not found: " + id);
    }
}
