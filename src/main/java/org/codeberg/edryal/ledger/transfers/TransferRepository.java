package org.codeberg.edryal.ledger.transfers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.common.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TransferRepository {

    private final JdbcClient jdbc;

    TransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Transfer transfer) {
        String query = """
                INSERT INTO transfers (id, kind, source_account_id, destination_account_id, amount, currency,
                                       description, journal_entry_id, created_at)
                VALUES (:id, :kind, :source, :destination, :amount, :currency, :description, :entryId, :createdAt)
                """;

        jdbc.sql(query)
                .param("id", transfer.id().value())
                .param("kind", transfer.kind().name())
                .param("source", transfer.source().map(AccountId::value).orElse(null))
                .param("destination", transfer.destination().map(AccountId::value).orElse(null))
                .param("amount", transfer.amount().minorUnits())
                .param("currency", transfer.amount().currency().getCurrencyCode())
                .param("description", transfer.description())
                .param("entryId", transfer.journalEntryId().value())
                .param("createdAt", transfer.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    Optional<Transfer> findById(TransferId id) {
        String query = """
                SELECT id, kind, source_account_id, destination_account_id, amount, currency,
                       description, journal_entry_id, created_at
                FROM transfers
                WHERE id = :id
                """;
        return jdbc.sql(query).param("id", id.value()).query(TransferRepository::transfer).optional();
    }

    private static Transfer transfer(ResultSet resultSet, int rowNum) throws SQLException {
        UUID source = resultSet.getObject("source_account_id", UUID.class);
        UUID destination = resultSet.getObject("destination_account_id", UUID.class);
        Currency currency = Currency.getInstance(resultSet.getString("currency"));

        return new Transfer(
                new TransferId(resultSet.getObject("id", UUID.class)),
                TransferKind.valueOf(resultSet.getString("kind")),
                Optional.ofNullable(source).map(AccountId::new),
                Optional.ofNullable(destination).map(AccountId::new),
                Money.of(resultSet.getLong("amount"), currency),
                resultSet.getString("description"),
                new JournalEntryId(resultSet.getObject("journal_entry_id", UUID.class)),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
