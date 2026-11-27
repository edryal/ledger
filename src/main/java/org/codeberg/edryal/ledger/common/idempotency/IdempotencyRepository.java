package org.codeberg.edryal.ledger.common.idempotency;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyRepository {

    private final JdbcClient jdbc;

    IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims a key for this transaction. Returns false if the key already exists.
     * If another transaction holds the same key but hasn't committed yet, this waits until it does.
     */
    boolean claim(String clientId, String key, byte[] requestHash) {
        String sql = """
                INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash)
                VALUES (:clientId, :key, :requestHash)
                ON CONFLICT DO NOTHING
                """;
        int inserted = jdbc.sql(sql)
                .param("clientId", clientId)
                .param("key", key)
                .param("requestHash", requestHash)
                .update();
        return inserted == 1;
    }

    Optional<StoredResponse> find(String clientId, String key) {
        String sql = """
                SELECT request_hash, response_status, response_location, response_body::text AS response_body
                FROM idempotency_keys
                WHERE client_id = :clientId AND idempotency_key = :key
                """;
        return jdbc.sql(sql)
                .param("clientId", clientId)
                .param("key", key)
                .query(IdempotencyRepository::storedResponse)
                .optional();
    }

    private static StoredResponse storedResponse(ResultSet resultSet, int rowNum) throws SQLException {
        return new StoredResponse(
                resultSet.getBytes("request_hash"),
                resultSet.getInt("response_status"),
                Optional.ofNullable(resultSet.getString("response_location")),
                resultSet.getString("response_body"));
    }

    void complete(String clientId, String key, int status, String location, String body) {
        String sql = """
                UPDATE idempotency_keys
                SET response_status = :status, response_location = :location, response_body = CAST(:body AS jsonb)
                WHERE client_id = :clientId AND idempotency_key = :key
                """;
        jdbc.sql(sql)
                .param("status", status)
                .param("location", location)
                .param("body", body)
                .param("clientId", clientId)
                .param("key", key)
                .update();
    }

    /** Deletes keys created before {@code cutoff}. Returns how many were deleted. */
    int deleteCreatedBefore(Instant cutoff) {
        String sql = "DELETE FROM idempotency_keys WHERE created_at < :cutoff";
        return jdbc.sql(sql).param("cutoff", cutoff.atOffset(ZoneOffset.UTC)).update();
    }
}
