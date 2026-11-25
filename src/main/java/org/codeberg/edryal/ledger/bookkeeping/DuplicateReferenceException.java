package org.codeberg.edryal.ledger.bookkeeping;

public class DuplicateReferenceException extends RuntimeException {

    public DuplicateReferenceException(String reference) {
        super("an entry with reference '" + reference + "' was already posted");
    }
}
