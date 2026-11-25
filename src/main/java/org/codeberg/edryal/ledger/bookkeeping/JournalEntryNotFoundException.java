package org.codeberg.edryal.ledger.bookkeeping;

public class JournalEntryNotFoundException extends RuntimeException {

    public JournalEntryNotFoundException(JournalEntryId id) {
        super("journal entry not found: " + id);
    }
}
