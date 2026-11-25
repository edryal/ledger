package org.codeberg.edryal.ledger.bookkeeping.application;

import java.util.List;
import java.util.Optional;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountNotFoundException;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryNotFoundException;
import org.codeberg.edryal.ledger.bookkeeping.domain.JournalEntry;
import org.codeberg.edryal.ledger.bookkeeping.infrastructure.AccountRepository;
import org.codeberg.edryal.ledger.bookkeeping.infrastructure.LedgerQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the ledger, used by the module's own REST endpoints. */
@Service
@Transactional(readOnly = true)
public class LedgerQueries {

    private final AccountRepository accounts;
    private final LedgerQueryRepository queries;

    LedgerQueries(AccountRepository accounts, LedgerQueryRepository queries) {
        this.accounts = accounts;
        this.queries = queries;
    }

    /**
     * One page of an account's postings, newest first.
     *
     * @throws AccountNotFoundException if the account doesn't exist
     */
    public StatementPage statement(AccountId accountId, Optional<Cursor> after, int limit) {
        if (accounts.findBalance(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        // Ask for one extra line: if it comes back, there is another page.
        List<StatementLine> lines = queries.findStatement(accountId, after, limit + 1);
        if (lines.size() <= limit) {
            return new StatementPage(lines, Optional.empty());
        }

        List<StatementLine> page = lines.subList(0, limit);
        StatementLine last = page.getLast();
        return new StatementPage(List.copyOf(page), Optional.of(new Cursor(last.postedAt(), last.postingId())));
    }

    /** @throws JournalEntryNotFoundException if the entry doesn't exist */
    public JournalEntry entry(JournalEntryId id) {
        return queries.findEntry(id).orElseThrow(() -> new JournalEntryNotFoundException(id));
    }
}
