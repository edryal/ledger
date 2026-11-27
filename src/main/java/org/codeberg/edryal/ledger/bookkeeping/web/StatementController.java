package org.codeberg.edryal.ledger.bookkeeping.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.Direction;
import org.codeberg.edryal.ledger.bookkeeping.application.Cursor;
import org.codeberg.edryal.ledger.bookkeeping.application.LedgerQueries;
import org.codeberg.edryal.ledger.bookkeeping.application.StatementLine;
import org.codeberg.edryal.ledger.bookkeeping.application.StatementPage;
import org.codeberg.edryal.ledger.common.web.MoneyBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
class StatementController {

    private final LedgerQueries queries;

    StatementController(LedgerQueries queries) {
        this.queries = queries;
    }

    record StatementResponse(List<LineResponse> data, String nextCursor) {
    }

    record LineResponse(
            UUID postingId,
            UUID entryId,
            String reference,
            String description,
            Direction direction,
            MoneyBody amount,
            Instant postedAt) {
    }

    @GetMapping("/v1/accounts/{id}/postings")
    StatementResponse statement(
            @PathVariable UUID id,
            @RequestParam Optional<String> cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        StatementPage page = queries.statement(new AccountId(id), cursor.map(Cursor::decode), limit);
        return new StatementResponse(
                page.lines().stream().map(StatementController::toResponse).toList(),
                page.next().map(Cursor::encode).orElse(null));
    }

    private static LineResponse toResponse(StatementLine line) {
        return new LineResponse(
                line.postingId(),
                line.entryId().value(),
                line.reference(),
                line.description(),
                line.direction(),
                MoneyBody.from(line.amount()),
                line.postedAt());
    }
}
