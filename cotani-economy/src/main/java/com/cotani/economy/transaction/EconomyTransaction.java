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

    static Deposit deposit(
            EconomyOperationId opId,
            UUID targetId,
            CurrencyId currId,
            BigDecimal amount,
            BigDecimal balBefore,
            BigDecimal balAfter,
            EconomyReason reason,
            Instant now) {
        return new Deposit(
                EconomyTransactionId.random(), opId, targetId, currId, amount, balBefore, balAfter, reason, now);
    }

    static Withdraw withdraw(
            EconomyOperationId opId,
            UUID sourceId,
            CurrencyId currId,
            BigDecimal amount,
            BigDecimal balBefore,
            BigDecimal balAfter,
            EconomyReason reason,
            Instant now) {
        return new Withdraw(
                EconomyTransactionId.random(), opId, sourceId, currId, amount, balBefore, balAfter, reason, now);
    }

    static Set set(
            EconomyOperationId opId,
            UUID targetId,
            CurrencyId currId,
            BigDecimal amount,
            BigDecimal balBefore,
            BigDecimal balAfter,
            EconomyReason reason,
            Instant now) {
        return new Set(EconomyTransactionId.random(), opId, targetId, currId, amount, balBefore, balAfter, reason, now);
    }

    static Transfer transfer(
            EconomyOperationId opId,
            UUID sourceId,
            UUID targetId,
            CurrencyId currId,
            BigDecimal amount,
            BigDecimal srcBalBefore,
            BigDecimal srcBalAfter,
            BigDecimal tgtBalBefore,
            BigDecimal tgtBalAfter,
            EconomyReason reason,
            Instant now) {
        return new Transfer(
                EconomyTransactionId.random(),
                opId,
                sourceId,
                targetId,
                currId,
                amount,
                srcBalBefore,
                srcBalAfter,
                tgtBalBefore,
                tgtBalAfter,
                reason,
                now);
    }

    EconomyTransactionId id();

    EconomyOperationId operationId();

    EconomyTransactionType type();

    CurrencyId currencyId();

    BigDecimal amount();

    EconomyReason reason();

    Instant createdAt();

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

    record Deposit(
            EconomyTransactionId id,
            EconomyOperationId operationId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal targetBalanceBefore,
            BigDecimal targetBalanceAfter,
            EconomyReason reason,
            Instant createdAt)
            implements EconomyTransaction {
        public Deposit {
            Objects.requireNonNull(id);
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(targetUserId);
            Objects.requireNonNull(currencyId);
            Objects.requireNonNull(amount);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(createdAt);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Transaction amount must be positive.");
            }
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.DEPOSIT;
        }

        @Override
        public @NonNull UUID targetUserId() {
            return targetUserId;
        }

        @Override
        public @NonNull BigDecimal targetBalanceBefore() {
            return targetBalanceBefore;
        }

        @Override
        public @NonNull BigDecimal targetBalanceAfter() {
            return targetBalanceAfter;
        }
    }

    record Withdraw(
            EconomyTransactionId id,
            EconomyOperationId operationId,
            UUID sourceUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal sourceBalanceBefore,
            BigDecimal sourceBalanceAfter,
            EconomyReason reason,
            Instant createdAt)
            implements EconomyTransaction {
        public Withdraw {
            Objects.requireNonNull(id);
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(sourceUserId);
            Objects.requireNonNull(currencyId);
            Objects.requireNonNull(amount);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(createdAt);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Transaction amount must be positive.");
            }
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.WITHDRAW;
        }

        @Override
        public @NonNull UUID sourceUserId() {
            return sourceUserId;
        }

        @Override
        public @NonNull BigDecimal sourceBalanceBefore() {
            return sourceBalanceBefore;
        }

        @Override
        public @NonNull BigDecimal sourceBalanceAfter() {
            return sourceBalanceAfter;
        }
    }

    record Set(
            EconomyTransactionId id,
            EconomyOperationId operationId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal targetBalanceBefore,
            BigDecimal targetBalanceAfter,
            EconomyReason reason,
            Instant createdAt)
            implements EconomyTransaction {
        public Set {
            Objects.requireNonNull(id);
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(targetUserId);
            Objects.requireNonNull(currencyId);
            Objects.requireNonNull(amount);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(createdAt);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Transaction amount must be positive.");
            }
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.SET;
        }

        @Override
        public @NonNull UUID targetUserId() {
            return targetUserId;
        }

        @Override
        public @NonNull BigDecimal targetBalanceBefore() {
            return targetBalanceBefore;
        }

        @Override
        public @NonNull BigDecimal targetBalanceAfter() {
            return targetBalanceAfter;
        }
    }

    record Transfer(
            EconomyTransactionId id,
            EconomyOperationId operationId,
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal sourceBalanceBefore,
            BigDecimal sourceBalanceAfter,
            BigDecimal targetBalanceBefore,
            BigDecimal targetBalanceAfter,
            EconomyReason reason,
            Instant createdAt)
            implements EconomyTransaction {
        public Transfer {
            Objects.requireNonNull(id);
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(sourceUserId);
            Objects.requireNonNull(targetUserId);
            Objects.requireNonNull(currencyId);
            Objects.requireNonNull(amount);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(createdAt);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Transaction amount must be positive.");
            }
        }

        @Override
        public EconomyTransactionType type() {
            return EconomyTransactionType.TRANSFER;
        }

        @Override
        public @NonNull UUID sourceUserId() {
            return sourceUserId;
        }

        @Override
        public @NonNull UUID targetUserId() {
            return targetUserId;
        }

        @Override
        public @NonNull BigDecimal sourceBalanceBefore() {
            return sourceBalanceBefore;
        }

        @Override
        public @NonNull BigDecimal sourceBalanceAfter() {
            return sourceBalanceAfter;
        }

        @Override
        public @NonNull BigDecimal targetBalanceBefore() {
            return targetBalanceBefore;
        }

        @Override
        public @NonNull BigDecimal targetBalanceAfter() {
            return targetBalanceAfter;
        }
    }
}
