package org.codeberg.edryal.ledger.bookkeeping.application;

import java.util.ArrayList;
import java.util.List;

import org.codeberg.edryal.ledger.bookkeeping.infrastructure.IntegrityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the whole ledger against the double-entry rules.
 * An empty result means the books are consistent.
 */
@Service
public class LedgerIntegrity {

    private final IntegrityRepository integrity;

    LedgerIntegrity(IntegrityRepository integrity) {
        this.integrity = integrity;
    }

    /** All problems found. Runs every check on one consistent snapshot of the database. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<String> check() {
        List<String> problems = new ArrayList<>();
        problems.addAll(integrity.unbalancedEntries());
        problems.addAll(integrity.balanceMismatches());
        problems.addAll(integrity.trialBalanceDifferences());
        problems.addAll(integrity.nonZeroHolds());
        return problems;
    }
}
