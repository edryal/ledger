package org.codeberg.edryal.ledger.bookkeeping;

import java.util.List;
import java.util.Objects;

/**
 * Command to post a journal entry.
 *
 * @param reference   identifies the business event, e.g. {@code transfer:<id>}. Posting the same reference twice fails.
 * @param description human-readable text shown in statements
 * @param postings    at least two postings, balanced per currency
 */
public record PostEntry(String reference, String description, List<Posting> postings) {

    public PostEntry {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(postings, "postings must not be null");
        postings = List.copyOf(postings);
    }
}
