package org.codeberg.edryal.ledger.bookkeeping;

/** The outcome of {@link Ledger#post(PostEntry)}: either posted or rejected for a business reason. */
public sealed interface PostResult {

    /** The entry was written and all balances were updated. */
    record Posted(JournalEntryId entryId) implements PostResult {
    }

    /** Nothing was written. {@code accountId} is the account that caused the rejection. */
    record Rejected(RejectionReason reason, AccountId accountId) implements PostResult {
    }
}
