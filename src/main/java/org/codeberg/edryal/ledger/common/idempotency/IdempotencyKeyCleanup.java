package org.codeberg.edryal.ledger.common.idempotency;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes idempotency keys once they are too old to be retried. */
@Component
class IdempotencyKeyCleanup {

    /** How long a client can retry a request with the same key. */
    static final Duration RETENTION = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

    private final IdempotencyRepository keys;

    IdempotencyKeyCleanup(IdempotencyRepository keys) {
        this.keys = keys;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    void deleteExpiredKeys() {
        int deleted = keys.deleteCreatedBefore(Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("deleted {} expired idempotency keys", deleted);
        }
    }
}
