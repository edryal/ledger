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

    private static final String STATEMENT_SELECT = """
            SELECT p.id, p.entry_id, e.reference, e.description, p.direction, p.amount, p.currency, p.posted_at
            FROM postings p
            JOIN journal_entries e ON e.id = p.entry_id
            WHERE p.account_id = :accountId
            """;

    private static final String STATEMENT_ORDER = """
            ORDER BY p.posted_at DESC, p.id DESC
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    public LedgerQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Postings of one account, newest first. With a cursor, only postings older than the cursor
     * are returned. Returns at most {@code limit} lines.
     */
    public List<StatementLine> findStatement(AccountId accountId, Optional<Cursor> after, int limit) {
        String sql = after.isPresent() ? STATEMENT_SELECT + "AND (p.posted_at, p.id) < (:cursorAt, :cursorId)\n" +
                STATEMENT_ORDER : STATEMENT_SELECT + STATEMENT_ORDER;

        var statement = jdbc.sql(sql)
                .param("accountId", accountId.value())
                .param("limit", limit);

        if (after.isPresent()) {
            statement = statement
                    .param("cursorAt", after.get().postedAt().atOffset(ZoneOffset.UTC))
                    .param("cursorId", after.get().postingId());
        }

        return statement.query(LedgerQueryRepository::statementLine).list();
    }

    public Optional<JournalEntry> findEntry(JournalEntryId id) {
        record Header(String reference, String description, OffsetDateTime postedAt) {
        }

        Optional<Header> header = jdbc.sql(
                "SELECT reference, description, posted_at FROM journal_entries WHERE id = :id")
                .param("id", id.value())
                .query((resultSet, rowNum) -> new Header(
                        resultSet.getString("reference"),
                        resultSet.getString("description"),
                        resultSet.getObject("posted_at", OffsetDateTime.class)))
                .optional();

        return header.map(h -> new JournalEntry(id, h.reference(), h.description(), h.postedAt().toInstant(),
                findPostings(id)));
    }

    private List<Posting> findPostings(JournalEntryId entryId) {
        return jdbc.sql("""
                SELECT account_id, direction, amount, currency
                FROM postings
                WHERE entry_id = :entryId
                ORDER BY direction DESC, id
                """)
                .param("entryId", entryId.value())
                .query((resultSet, rowNum) -> new Posting(
                        new AccountId(resultSet.getObject("account_id", UUID.class)),
                        Direction.valueOf(resultSet.getString("direction")),
                        money(resultSet)))
                .list();
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
        return Money.of(resultSet.getLong("amount"), Currency.getInstance(resultSet.getString("currency")));
    }
}
