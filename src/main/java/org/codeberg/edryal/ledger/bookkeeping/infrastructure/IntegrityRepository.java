package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Queries that look for ledger data breaking the double-entry rules. Each returns one line per problem. */
@Repository
public class IntegrityRepository {

    private final JdbcClient jdbc;

    public IntegrityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Entries whose debits and credits differ in some currency. */
    public List<String> unbalancedEntries() {
        return jdbc.sql("""
                SELECT e.reference, p.currency,
                       sum(CASE p.direction WHEN 'DEBIT' THEN p.amount ELSE -p.amount END) AS net
                FROM journal_entries e
                JOIN postings p ON p.entry_id = e.id
                GROUP BY e.id, e.reference, p.currency
                HAVING sum(CASE p.direction WHEN 'DEBIT' THEN p.amount ELSE -p.amount END) <> 0
                """)
                .query((resultSet, rowNum) -> "entry %s is unbalanced in %s by %d".formatted(
                        resultSet.getString("reference"), resultSet.getString("currency"), resultSet.getLong("net")))
                .list();
    }

    /** Accounts whose stored balance differs from the balance recomputed from their postings. */
    public List<String> balanceMismatches() {
        return jdbc.sql(
                """
                        SELECT a.id, b.balance,
                               coalesce(sum(CASE WHEN p.direction = (CASE a.category WHEN 'ASSET' THEN 'DEBIT' ELSE 'CREDIT' END)
                                                 THEN p.amount ELSE -p.amount END), 0) AS computed
                        FROM accounts a
                        JOIN account_balances b ON b.account_id = a.id
                        LEFT JOIN postings p ON p.account_id = a.id
                        GROUP BY a.id, b.balance
                        HAVING b.balance <> coalesce(sum(CASE WHEN p.direction = (CASE a.category WHEN 'ASSET' THEN 'DEBIT' ELSE 'CREDIT' END)
                                                              THEN p.amount ELSE -p.amount END), 0)
                        """)
                .query((resultSet, rowNum) -> "account %s has stored balance %d but postings sum to %d".formatted(
                        resultSet.getObject("id"), resultSet.getLong("balance"), resultSet.getLong("computed")))
                .list();
    }

    /** Currencies where total debits differ from total credits across the whole ledger. */
    public List<String> trialBalanceDifferences() {
        return jdbc.sql("""
                SELECT currency,
                       coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
                       coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
                FROM postings
                GROUP BY currency
                HAVING coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)
                    <> coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0)
                """)
                .query((resultSet, rowNum) -> "%s debits %d but credits %d".formatted(resultSet.getString("currency"),
                        resultSet.getLong("debits"), resultSet.getLong("credits")))
                .list();
    }

    /**
     * Holds accounts that are not zero. Valid until payments exist (M3),
     * when this becomes "holds = open authorizations".
     */
    public List<String> nonZeroHolds() {
        return jdbc.sql("""
                SELECT a.code, b.balance
                FROM accounts a
                JOIN account_balances b ON b.account_id = a.id
                WHERE a.type = 'PLATFORM_HOLDS' AND b.balance <> 0
                """)
                .query((resultSet, rowNum) -> "%s is %d but no authorizations are open"
                        .formatted(resultSet.getString("code"), resultSet.getLong("balance")))
                .list();
    }
}
