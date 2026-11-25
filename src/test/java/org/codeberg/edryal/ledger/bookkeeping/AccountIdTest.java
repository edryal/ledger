package org.codeberg.edryal.ledger.bookkeeping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountIdTest {

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new AccountId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parsesItsOwnToString() {
        AccountId id = AccountId.newId();
        assertThat(AccountId.parse(id.toString())).isEqualTo(id);
    }

    @Test
    void rejectsMalformedText() {
        assertThatThrownBy(() -> AccountId.parse("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newIdsAreDifferent() {
        assertThat(AccountId.newId()).isNotEqualTo(AccountId.newId());
    }
}
