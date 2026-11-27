package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.codeberg.edryal.ledger.bookkeeping.DuplicateReferenceException;
import org.codeberg.edryal.ledger.bookkeeping.Posting;
import org.codeberg.edryal.ledger.bookkeeping.domain.JournalEntry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JournalEntryRepository {

    private final JdbcClient jdbc;

    public JournalEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(JournalEntry entry) {
        OffsetDateTime postedAt = entry.postedAt().atOffset(ZoneOffset.UTC);

        try {
            jdbc.sql("""
                    INSERT INTO journal_entries (id, reference, description, posted_at)
                    VALUES (:id, :reference, :description, :postedAt)
                    """)
                    .param("id", entry.id().value())
                    .param("reference", entry.reference())
                    .param("description", entry.description())
                    .param("postedAt", postedAt)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateReferenceException(entry.reference());
        }

        // TODO: one multi-row INSERT instead of one INSERT per posting (2-3 round trips per entry)
        for (Posting posting : entry.postings()) {
            jdbc.sql("""
                    INSERT INTO postings (id, entry_id, account_id, direction, amount, currency, posted_at)
                    VALUES (:id, :entryId, :accountId, :direction, :amount, :currency, :postedAt)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("entryId", entry.id().value())
                    .param("accountId", posting.accountId().value())
                    .param("direction", posting.direction().name())
                    .param("amount", posting.amount().minorUnits())
                    .param("currency", posting.amount().currency().getCurrencyCode())
                    .param("postedAt", postedAt)
                    .update();
        }
    }
}
