package org.codeberg.edryal.ledger.bookkeeping.application;

import java.time.Instant;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.Direction;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.common.Money;

/** One posting as it appears on an account statement. */
public record StatementLine(UUID postingId, JournalEntryId entryId, String reference, String description,
                            Direction direction, Money amount, Instant postedAt) {
}
