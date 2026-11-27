package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
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
        String sql = """
                SELECT e.reference, p.currency,
                       sum(CASE p.direction WHEN 'DEBIT' THEN p.amount ELSE -p.amount END) AS net
                FROM journal_entries e
                JOIN postings p ON p.entry_id = e.id
                GROUP BY e.id, e.reference, p.currency
                HAVING sum(CASE p.direction WHEN 'DEBIT' THEN p.amount ELSE -p.amount END) <> 0
                """;
        return jdbc.sql(sql).query(IntegrityRepository::unbalancedEntry).list();
    }

    private static String unbalancedEntry(ResultSet resultSet, int rowNum) throws SQLException {
        String reference = resultSet.getString("reference");
        String currency = resultSet.getString("currency");
        long net = resultSet.getLong("net");
        return "entry %s is unbalanced in %s by %d".formatted(reference, currency, net);
    }

    /** Accounts whose stored balance differs from the balance recomputed from their postings. */
    public List<String> balanceMismatches() {
        String sql = """
                WITH computed AS (
                    SELECT a.id,
                           coalesce(sum(CASE WHEN p.direction = a.normal_side THEN p.amount ELSE -p.amount END), 0)
                               AS balance
                    FROM (SELECT id, CASE category WHEN 'ASSET' THEN 'DEBIT' ELSE 'CREDIT' END AS normal_side
                          FROM accounts) a
                    LEFT JOIN postings p ON p.account_id = a.id
                    GROUP BY a.id
                )
                SELECT c.id, b.balance AS stored, c.balance AS computed
                FROM computed c
                JOIN account_balances b ON b.account_id = c.id
                WHERE b.balance <> c.balance
                """;
        return jdbc.sql(sql).query(IntegrityRepository::balanceMismatch).list();
    }

    private static String balanceMismatch(ResultSet resultSet, int rowNum) throws SQLException {
        Object accountId = resultSet.getObject("id");
        long stored = resultSet.getLong("stored");
        long computed = resultSet.getLong("computed");
        return "account %s has stored balance %d but postings sum to %d".formatted(accountId, stored, computed);
    }

    /** Currencies where total debits differ from total credits across the whole ledger. */
    public List<String> trialBalanceDifferences() {
        String sql = """
                SELECT currency,
                       coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
                       coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
                FROM postings
                GROUP BY currency
                HAVING coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)
                    <> coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0)
                """;
        return jdbc.sql(sql).query(IntegrityRepository::trialBalanceDifference).list();
    }

    private static String trialBalanceDifference(ResultSet resultSet, int rowNum) throws SQLException {
        String currency = resultSet.getString("currency");
        long debits = resultSet.getLong("debits");
        long credits = resultSet.getLong("credits");
        return "%s debits %d but credits %d".formatted(currency, debits, credits);
    }

    /** Holds accounts that are not zero. Becomes "holds = open authorizations" once payments exist (M3). */
    public List<String> nonZeroHolds() {
        String sql = """
                SELECT a.code, b.balance
                FROM accounts a
                JOIN account_balances b ON b.account_id = a.id
                WHERE a.type = 'PLATFORM_HOLDS' AND b.balance <> 0
                """;
        return jdbc.sql(sql).query(IntegrityRepository::nonZeroHold).list();
    }

    private static String nonZeroHold(ResultSet resultSet, int rowNum) throws SQLException {
        String code = resultSet.getString("code");
        long balance = resultSet.getLong("balance");
        return "%s is %d but no authorizations are open".formatted(code, balance);
    }
}
