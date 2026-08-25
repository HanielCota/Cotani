package com.cotani.economy.transaction;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public sealed interface EconomyTransaction
        permits EconomyTransaction.Deposit,
                EconomyTransaction.Withdraw,
                EconomyTransaction.Set,
                EconomyTransaction.Transfer {

    static Deposit deposit(EconomyTransactionDetails details, EconomyBalanceChange targetChange) {
        return new Deposit(EconomyTransactionId.random(), details, targetChange);
    }

    static Withdraw withdraw(EconomyTransactionDetails details, EconomyBalanceChange sourceChange) {
        return new Withdraw(EconomyTransactionId.random(), details, sourceChange);
    }

    static Set set(EconomyTransactionDetails details, EconomyBalanceChange targetChange) {
        return new Set(EconomyTransactionId.random(), details, targetChange);
    }

    static Transfer transfer(
            EconomyTransactionDetails details, EconomyBalanceChange sourceChange, EconomyBalanceChange targetChange) {
        return new Transfer(EconomyTransactionId.random(), details, sourceChange, targetChange);
    }

    EconomyTransactionId id();

    EconomyTransactionDetails details();

    default EconomyOperationId operationId() {
        return details().operationId();
    }

    EconomyTransactionType type();

    default CurrencyId currencyId() {
        return details().currencyId();
    }

    default BigDecimal amount() {
        return details().amount();
    }

    default EconomyReason reason() {
        return details().reason();
    }

    default Instant createdAt() {
        return details().createdAt();
    }

    default Optional<UUID> source() {
        return Optional.ofNullable(sourceUserId());
    }

    default Optional<UUID> target() {
        return Optional.ofNullable(targetUserId());
    }

    default @Nullable UUID sourceUserId() {
        return null;
    }

    default @Nullable UUID targetUserId() {
        return null;
    }

    default @Nullable BigDecimal sourceBalanceBefore() {
        return null;
    }

    default @Nullable BigDecimal sourceBalanceAfter() {
        return null;
    }

    default @Nullable BigDecimal targetBalanceBefore() {
        return null;
    }

    default @Nullable BigDecimal targetBalanceAfter() {
        return null;
    }

    default Optional<BigDecimal> optionalSourceBalanceBefore() {
        return Optional.ofNullable(sourceBalanceBefore());
    }

    default Optional<BigDecimal> optionalSourceBalanceAfter() {
        return Optional.ofNullable(sourceBalanceAfter());
    }

    default Optional<BigDecimal> optionalTargetBalanceBefore() {
        return Optional.ofNullable(targetBalanceBefore());
    }

    default Optional<BigDecimal> optionalTargetBalanceAfter() {
        return Optional.ofNullable(targetBalanceAfter());
    }

    private static void requirePositiveAmount(EconomyTransactionDetails details) {
        if (details.amount().signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive.");
        }
    }

    record Deposit(EconomyTransactionId id, EconomyTransactionDetails details, EconomyBalanceChange targetChange)
            implements EconomyTransaction {
        public Deposit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(targetChange, "targetChange");
            requirePositiveAmount(details);
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.DEPOSIT;
        }

        @Override
        public Optional<UUID> source() {
            return Optional.empty();
        }

        @Override
        public @NonNull UUID targetUserId() {
            return targetChange.userId();
        }

        @Override
        public @NonNull BigDecimal targetBalanceBefore() {
            return targetChange.before();
        }

        @Override
        public @NonNull BigDecimal targetBalanceAfter() {
            return targetChange.after();
        }
    }

    record Withdraw(EconomyTransactionId id, EconomyTransactionDetails details, EconomyBalanceChange sourceChange)
            implements EconomyTransaction {
        public Withdraw {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(sourceChange, "sourceChange");
            requirePositiveAmount(details);
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.WITHDRAW;
        }

        @Override
        public Optional<UUID> target() {
            return Optional.empty();
        }

        @Override
        public @NonNull UUID sourceUserId() {
            return sourceChange.userId();
        }

        @Override
        public @NonNull BigDecimal sourceBalanceBefore() {
            return sourceChange.before();
        }

        @Override
        public @NonNull BigDecimal sourceBalanceAfter() {
            return sourceChange.after();
        }
    }

    record Set(EconomyTransactionId id, EconomyTransactionDetails details, EconomyBalanceChange targetChange)
            implements EconomyTransaction {
        public Set {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(targetChange, "targetChange");

            if (details.amount().signum() < 0) {
                throw new IllegalArgumentException("Set balance amount cannot be negative.");
            }
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.SET;
        }

        @Override
        public Optional<UUID> source() {
            return Optional.empty();
        }

        @Override
        public @NonNull UUID targetUserId() {
            return targetChange.userId();
        }

        @Override
        public @NonNull BigDecimal targetBalanceBefore() {
            return targetChange.before();
        }

        @Override
        public @NonNull BigDecimal targetBalanceAfter() {
            return targetChange.after();
        }
    }

    record Transfer(
            EconomyTransactionId id,
            EconomyTransactionDetails details,
            EconomyBalanceChange sourceChange,
            EconomyBalanceChange targetChange)
            implements EconomyTransaction {
        public Transfer {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(sourceChange, "sourceChange");
            Objects.requireNonNull(targetChange, "targetChange");
            requirePositiveAmount(details);
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.TRANSFER;
        }

        @Override
        public @NonNull UUID sourceUserId() {
            return sourceChange.userId();
        }

        @Override
        public @NonNull UUID targetUserId() {
            return targetChange.userId();
        }

        @Override
        public @NonNull BigDecimal sourceBalanceBefore() {
            return sourceChange.before();
        }

        @Override
        public @NonNull BigDecimal sourceBalanceAfter() {
            return sourceChange.after();
        }

        @Override
        public @NonNull BigDecimal targetBalanceBefore() {
            return targetChange.before();
        }

        @Override
        public @NonNull BigDecimal targetBalanceAfter() {
            return targetChange.after();
        }
    }
}
