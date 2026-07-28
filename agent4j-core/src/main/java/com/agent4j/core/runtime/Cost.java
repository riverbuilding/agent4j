package com.agent4j.core.runtime;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Cost(BigDecimal amount, Currency currency) {
    public Cost {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("cost amount must be non-negative");
        }
    }

    public static Cost zero(Currency currency) {
        return new Cost(BigDecimal.ZERO, currency);
    }

    public Cost plus(Cost other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("cannot add costs with different currencies");
        }
        return new Cost(amount.add(other.amount), currency);
    }
}
