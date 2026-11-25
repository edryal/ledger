package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.domain.Account;
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
}
