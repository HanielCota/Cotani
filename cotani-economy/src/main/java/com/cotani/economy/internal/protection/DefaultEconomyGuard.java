package com.cotani.economy.internal.protection;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.InvalidAmountException;
import com.cotani.economy.exception.SameEconomyAccountTransferException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class DefaultEconomyGuard implements EconomyGuard {

    private final EconomySettings settings;
    private final int decimalPlaces;

    public DefaultEconomyGuard(EconomySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        decimalPlaces = settings.defaultCurrency().decimalPlaces();
    }

    @Override
    public BigDecimal normalizeAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        if (amount.signum() <= 0) {
            throw new InvalidAmountException(amount, "amount must be greater than zero");
        }

        if (amount.scale() > decimalPlaces) {
            throw new InvalidAmountException(amount, "amount scale cannot be greater than " + decimalPlaces);
        }

        if (amount.compareTo(settings.maximumOperationAmount()) > 0) {
            throw new InvalidAmountException(
                    amount, "amount cannot be greater than " + settings.maximumOperationAmount());
        }

        return amount.setScale(decimalPlaces);
    }

    @Override
    public void validateBalanceAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new InvalidAmountException(amount, "balance cannot be negative");
        }

        if (amount.scale() > decimalPlaces) {
            throw new InvalidAmountException(amount, "balance scale cannot be greater than " + decimalPlaces);
        }

        if (amount.compareTo(settings.maximumBalance()) > 0) {
            throw new InvalidAmountException(amount, "balance cannot be greater than " + settings.maximumBalance());
        }
    }

    @Override
    public void validateUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
    }

    @Override
    public void validateCurrencyId(CurrencyId currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
    }

    @Override
    public void validateReason(EconomyReason reason) {
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public void validateOperationId(EconomyOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
    }

    @Override
    public void validateTransfer(UUID sourceUserId, UUID targetUserId, BigDecimal amount) {
        validateUserId(sourceUserId);
        validateUserId(targetUserId);
        normalizeAmount(amount);

        if (sourceUserId.equals(targetUserId)) {
            throw new SameEconomyAccountTransferException(sourceUserId);
        }
    }
}
