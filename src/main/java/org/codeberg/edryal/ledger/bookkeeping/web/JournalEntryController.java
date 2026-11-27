package org.codeberg.edryal.ledger.bookkeeping.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.Direction;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.bookkeeping.application.LedgerQueries;
import org.codeberg.edryal.ledger.bookkeeping.domain.JournalEntry;
import org.codeberg.edryal.ledger.common.web.MoneyBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JournalEntryController {

    private final LedgerQueries queries;

    JournalEntryController(LedgerQueries queries) {
        this.queries = queries;
    }

    record EntryResponse(
            UUID id,
            String reference,
            String description,
            Instant postedAt,
            List<PostingResponse> postings) {
    }

    record PostingResponse(UUID accountId, Direction direction, MoneyBody amount) {
    }

    @GetMapping("/v1/journal-entries/{id}")
    EntryResponse get(@PathVariable UUID id) {
        JournalEntry entry = queries.entry(new JournalEntryId(id));
        return new EntryResponse(
                entry.id().value(),
                entry.reference(),
                entry.description(),
                entry.postedAt(),
                entry.postings()
                        .stream()
                        .map(p -> new PostingResponse(p.accountId().value(), p.direction(), MoneyBody.from(p.amount())))
                        .toList());
    }
}
