package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.domain.Account;
import org.codeberg.edryal.ledger.bookkeeping.domain.AccountCategory;
import org.codeberg.edryal.ledger.common.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Account account, AccountType type, UUID ownerId) {
        String insertAccount = """
                INSERT INTO accounts (id, type, category, currency, owner_id)
                VALUES (:id, :type, :category, :currency, :ownerId)
                """;
        jdbc.sql(insertAccount)
                .param("id", account.id().value())
                .param("type", type.name())
                .param("category", account.category().name())
                .param("currency", account.currency().getCurrencyCode())
                .param("ownerId", ownerId)
                .update();

        String insertBalance = """
                INSERT INTO account_balances (account_id, allow_negative)
                VALUES (:id, :allowNegative)
                """;
        jdbc.sql(insertBalance)
                .param("id", account.id().value())
                .param("allowNegative", account.allowNegative())
                .update();
    }

    public Optional<Money> findBalance(AccountId id) {
        String sql = """
                SELECT b.balance, a.currency
                FROM account_balances b
                JOIN accounts a ON a.id = b.account_id
                WHERE b.account_id = :id
                """;
        return jdbc.sql(sql).param("id", id.value()).query(AccountRepository::balance).optional();
    }

    private static Money balance(ResultSet resultSet, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(resultSet.getString("currency"));
        return Money.of(resultSet.getLong("balance"), currency);
    }

    public Optional<AccountId> findIdByCode(String code) {
        String sql = "SELECT id FROM accounts WHERE code = :code";
        return jdbc.sql(sql).param("code", code).query(AccountRepository::accountId).optional();
    }

    private static AccountId accountId(ResultSet resultSet, int rowNum) throws SQLException {
        return new AccountId(resultSet.getObject("id", UUID.class));
    }

    /**
     * Loads accounts with their balances and locks the balance rows until the transaction ends.
     * Accounts that don't exist are missing from the result.
     */
    public Map<AccountId, AccountBalance> lockBalances(Collection<AccountId> ids) {
        String sql = """
                SELECT a.id, a.category, a.currency, b.balance, b.allow_negative
                FROM account_balances b
                JOIN accounts a ON a.id = b.account_id
                WHERE b.account_id IN (:ids)
                ORDER BY b.account_id
                FOR UPDATE OF b
                """;
        return jdbc.sql(sql)
                .param("ids", ids.stream().map(AccountId::value).toList())
                .query(AccountRepository::accountBalance)
                .stream()
                .collect(Collectors.toMap(row -> row.account().id(), Function.identity()));
    }

    private static AccountBalance accountBalance(ResultSet resultSet, int rowNum) throws SQLException {
        var account = new Account(
                new AccountId(resultSet.getObject("id", UUID.class)),
                AccountCategory.valueOf(resultSet.getString("category")),
                Currency.getInstance(resultSet.getString("currency")),
                resultSet.getBoolean("allow_negative"));
        return new AccountBalance(account, resultSet.getLong("balance"));
    }

    public void updateBalance(AccountId id, long balance) {
        String sql = """
                UPDATE account_balances
                SET balance = :balance, updated_at = now()
                WHERE account_id = :id
                """;
        jdbc.sql(sql).param("balance", balance).param("id", id.value()).update();
    }
}
