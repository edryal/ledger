package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

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
        jdbc.sql("""
                INSERT INTO accounts (id, type, category, currency, owner_id)
                VALUES (:id, :type, :category, :currency, :ownerId)
                """)
                .param("id", account.id().value())
                .param("type", type.name())
                .param("category", account.category().name())
                .param("currency", account.currency().getCurrencyCode())
                .param("ownerId", ownerId)
                .update();

        jdbc.sql("""
                INSERT INTO account_balances (account_id, allow_negative)
                VALUES (:id, :allowNegative)
                """)
                .param("id", account.id().value())
                .param("allowNegative", account.allowNegative())
                .update();
    }

    public Optional<Money> findBalance(AccountId id) {
        return jdbc.sql("""
                SELECT b.balance, a.currency
                FROM account_balances b
                JOIN accounts a ON a.id = b.account_id
                WHERE b.account_id = :id
                """)
                .param("id", id.value())
                .query((resultSet, rowNum) -> Money.of(
                        resultSet.getLong("balance"),
                        Currency.getInstance(resultSet.getString("currency"))))
                .optional();
    }

    public Optional<AccountId> findIdByCode(String code) {
        return jdbc.sql("SELECT id FROM accounts WHERE code = :code")
                .param("code", code)
                .query((resultSet, rowNum) -> new AccountId(resultSet.getObject("id", UUID.class)))
                .optional();
    }

    /**
     * Loads accounts with their balances and locks the balance rows until the transaction ends.
     * Rows are locked in account ID order, so concurrent transactions can't deadlock each other.
     * Accounts that don't exist are missing from the result.
     */
    public Map<AccountId, AccountBalance> lockBalances(Collection<AccountId> ids) {
        return jdbc.sql("""
                SELECT a.id, a.category, a.currency, b.balance, b.allow_negative
                FROM account_balances b
                JOIN accounts a ON a.id = b.account_id
                WHERE b.account_id IN (:ids)
                ORDER BY b.account_id
                FOR UPDATE OF b
                """)
                .param("ids", ids.stream().map(AccountId::value).toList())
                .query((resultSet, rowNum) -> new AccountBalance(
                        new Account(
                                new AccountId(resultSet.getObject("id", UUID.class)),
                                AccountCategory.valueOf(resultSet.getString("category")),
                                Currency.getInstance(resultSet.getString("currency")),
                                resultSet.getBoolean("allow_negative")),
                        resultSet.getLong("balance")))
                .stream()
                .collect(Collectors.toMap(row -> row.account().id(), Function.identity()));
    }

    public void updateBalance(AccountId id, long balance) {
        jdbc.sql("""
                UPDATE account_balances
                SET balance = :balance, updated_at = now()
                WHERE account_id = :id
                """)
                .param("balance", balance)
                .param("id", id.value())
                .update();
    }
}
