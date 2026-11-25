package org.codeberg.edryal.ledger.bookkeeping.application;

import java.util.List;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports the ledger's integrity at {@code /actuator/health/ledger}. DOWN if any check finds a problem. */
@Component("ledger")
public class LedgerHealthIndicator implements HealthIndicator {

    private final LedgerIntegrity integrity;

    LedgerHealthIndicator(LedgerIntegrity integrity) {
        this.integrity = integrity;
    }

    @Override
    public Health health() {
        List<String> problems = integrity.check();
        return problems.isEmpty() ? Health.up().build() : Health.down().withDetail("problems", problems).build();
    }
}
