package org.codeberg.edryal.ledger.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.codeberg.edryal.ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyKeyCleanupTest {

    @Autowired
    IdempotencyKeyCleanup cleanup;

    @Autowired
    JdbcClient jdbc;

    @Test
    void deletesKeysOlderThanRetentionAndKeepsRecentOnes() {
        String expired = insertKey(Instant.now().minus(25, ChronoUnit.HOURS));
        String recent = insertKey(Instant.now().minus(1, ChronoUnit.HOURS));

        cleanup.deleteExpiredKeys();

        assertThat(exists(expired)).isFalse();
        assertThat(exists(recent)).isTrue();
    }

    private String insertKey(Instant createdAt) {
        String key = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, created_at)
                VALUES ('test', :key, '\\x00', :createdAt)
                """;
        jdbc.sql(sql).param("key", key).param("createdAt", createdAt.atOffset(ZoneOffset.UTC)).update();
        return key;
    }

    private boolean exists(String key) {
        String sql = "SELECT count(*) FROM idempotency_keys WHERE client_id = 'test' AND idempotency_key = :key";
        return jdbc.sql(sql).param("key", key).query(Long.class).single() == 1;
    }
}
