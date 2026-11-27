package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.Direction;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.bookkeeping.Posting;
import org.codeberg.edryal.ledger.bookkeeping.application.Cursor;
import org.codeberg.edryal.ledger.bookkeeping.application.StatementLine;
import org.codeberg.edryal.ledger.bookkeeping.domain.JournalEntry;
import org.codeberg.edryal.ledger.common.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-only queries: account statements and journal entries. */
@Repository
public class LedgerQueryRepository {

    private final JdbcClient jdbc;

    public LedgerQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Postings of one account, newest first. With a cursor, only postings older than the cursor
     * are returned. Returns at most {@code limit} lines.
     */
    public List<StatementLine> findStatement(AccountId accountId, Optional<Cursor> after, int limit) {
        if (after.isEmpty()) {
            String sql = """
                    SELECT p.id, p.entry_id, e.reference, e.description, p.direction, p.amount, p.currency, p.posted_at
                    FROM postings p
                    JOIN journal_entries e ON e.id = p.entry_id
                    WHERE p.account_id = :accountId
                    ORDER BY p.posted_at DESC, p.id DESC
                    LIMIT :limit
                    """;
            return jdbc.sql(sql)
                    .param("accountId", accountId.value())
                    .param("limit", limit)
                    .query(LedgerQueryRepository::statementLine)
                    .list();
        }

        Cursor cursor = after.get();
        String sql = """
                SELECT p.id, p.entry_id, e.reference, e.description, p.direction, p.amount, p.currency, p.posted_at
                FROM postings p
                JOIN journal_entries e ON e.id = p.entry_id
                WHERE p.account_id = :accountId
                  AND (p.posted_at, p.id) < (:cursorAt, :cursorId)
                ORDER BY p.posted_at DESC, p.id DESC
                LIMIT :limit
                """;
        return jdbc.sql(sql)
                .param("accountId", accountId.value())
                .param("cursorAt", cursor.postedAt().atOffset(ZoneOffset.UTC))
                .param("cursorId", cursor.postingId())
                .param("limit", limit)
                .query(LedgerQueryRepository::statementLine)
                .list();
    }

    public Optional<JournalEntry> findEntry(JournalEntryId id) {
        String sql = "SELECT reference, description, posted_at FROM journal_entries WHERE id = :id";
        Optional<EntryHeader> header = jdbc.sql(sql)
                .param("id", id.value())
                .query(LedgerQueryRepository::entryHeader)
                .optional();
        return header.map(found -> found.toEntry(id, findPostings(id)));
    }

    private List<Posting> findPostings(JournalEntryId entryId) {
        String sql = """
                SELECT account_id, direction, amount, currency
                FROM postings
                WHERE entry_id = :entryId
                ORDER BY direction DESC, id
                """;
        return jdbc.sql(sql).param("entryId", entryId.value()).query(LedgerQueryRepository::posting).list();
    }

    /** The journal_entries row, before its postings are loaded. */
    private record EntryHeader(String reference, String description, OffsetDateTime postedAt) {

        JournalEntry toEntry(JournalEntryId id, List<Posting> postings) {
            return new JournalEntry(id, reference, description, postedAt.toInstant(), postings);
        }
    }

    private static EntryHeader entryHeader(ResultSet resultSet, int rowNum) throws SQLException {
        return new EntryHeader(
                resultSet.getString("reference"),
                resultSet.getString("description"),
                resultSet.getObject("posted_at", OffsetDateTime.class));
    }

    private static Posting posting(ResultSet resultSet, int rowNum) throws SQLException {
        return new Posting(
                new AccountId(resultSet.getObject("account_id", UUID.class)),
                Direction.valueOf(resultSet.getString("direction")),
                money(resultSet));
    }

    private static StatementLine statementLine(ResultSet resultSet, int rowNum) throws SQLException {
        return new StatementLine(
                resultSet.getObject("id", UUID.class),
                new JournalEntryId(resultSet.getObject("entry_id", UUID.class)),
                resultSet.getString("reference"),
                resultSet.getString("description"),
                Direction.valueOf(resultSet.getString("direction")),
                money(resultSet),
                resultSet.getObject("posted_at", OffsetDateTime.class).toInstant());
    }

    private static Money money(ResultSet resultSet) throws SQLException {
        Currency currency = Currency.getInstance(resultSet.getString("currency"));
        return Money.of(resultSet.getLong("amount"), currency);
    }
}
