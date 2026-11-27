package org.codeberg.edryal.ledger.common.idempotency;

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
        int inserted = jdbc.sql("""
                INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash)
                VALUES (:clientId, :key, :requestHash)
                ON CONFLICT DO NOTHING
                """).param("clientId", clientId).param("key", key).param("requestHash", requestHash).update();
        return inserted == 1;
    }

    Optional<StoredResponse> find(String clientId, String key) {
        return jdbc.sql("""
                SELECT request_hash, response_status, response_location, response_body::text AS response_body
                FROM idempotency_keys
                WHERE client_id = :clientId AND idempotency_key = :key
                """)
                .param("clientId", clientId)
                .param("key", key)
                .query((resultSet, rowNum) -> new StoredResponse(
                        resultSet.getBytes("request_hash"), resultSet.getInt("response_status"),
                        Optional.ofNullable(resultSet.getString("response_location")),
                        resultSet.getString("response_body")))
                .optional();
    }

    void complete(String clientId, String key, int status, String location, String body) {
        jdbc.sql("""
                UPDATE idempotency_keys
                SET response_status = :status, response_location = :location, response_body = CAST(:body AS jsonb)
                WHERE client_id = :clientId AND idempotency_key = :key
                """)
                .param("status", status)
                .param("location", location)
                .param("body", body)
                .param("clientId", clientId)
                .param("key", key)
                .update();
    }
}
