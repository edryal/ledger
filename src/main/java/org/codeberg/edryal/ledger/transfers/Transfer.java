package org.codeberg.edryal.ledger.transfers;

import java.time.Instant;
import java.util.Optional;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.common.Money;

/**
 * A completed movement of money. Transfers are only stored once their journal entry is posted,
 * so there is no pending or failed state.
 *
 * @param source      empty for deposits
 * @param destination empty for withdrawals
 */
record Transfer(
        TransferId id,
        TransferKind kind,
        Optional<AccountId> source,
        Optional<AccountId> destination,
        Money amount,
        String description,
        JournalEntryId journalEntryId,
        Instant createdAt) {
}
