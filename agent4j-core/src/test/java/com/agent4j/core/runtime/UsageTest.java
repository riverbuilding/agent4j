package com.agent4j.core.runtime;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageTest {
    @Test
    void addsUsageAndComputesTotalTokens() {
        Usage total = new Usage(10, 4, 3, 2).plus(new Usage(1, 2, 3, 4));

        assertThat(total.inputTokens()).isEqualTo(11);
        assertThat(total.outputTokens()).isEqualTo(6);
        assertThat(total.cachedInputTokens()).isEqualTo(6);
        assertThat(total.reasoningTokens()).isEqualTo(6);
        assertThat(total.totalTokens()).isEqualTo(23);
    }

    @Test
    void addsCostsOnlyForSameCurrency() {
        Currency usd = Currency.getInstance("USD");
        Cost cost = new Cost(new BigDecimal("0.10"), usd).plus(new Cost(new BigDecimal("0.25"), usd));

        assertThat(cost.amount()).isEqualByComparingTo("0.35");
        assertThatThrownBy(() -> cost.plus(new Cost(new BigDecimal("0.01"), Currency.getInstance("CAD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different currencies");
    }
}
