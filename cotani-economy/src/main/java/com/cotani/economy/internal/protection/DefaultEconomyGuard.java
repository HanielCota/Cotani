package com.cotani.economy.internal.protection;

import com.cotani.api.InternalApi;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.InvalidAmountException;
import com.cotani.economy.exception.SameEconomyAccountTransferException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@InternalApi
public final class DefaultEconomyGuard implements EconomyGuard {

    private static final String CURRENCY_ID_PARAM = "currencyId";

    private final EconomySettings settings;

    public DefaultEconomyGuard(EconomySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public BigDecimal normalizeAmount(BigDecimal amount) {
        return normalizeAmount(settings.defaultCurrency().id(), amount);
    }

    @Override
    public BigDecimal normalizeAmount(CurrencyId currencyId, BigDecimal amount) {
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        Objects.requireNonNull(amount, "amount");
        validateCurrencyId(currencyId);

        int decimalPlaces = settings.decimalPlaces(currencyId);

        if (amount.signum() <= 0) {
            throw new InvalidAmountException(amount, "amount must be greater than zero");
        }

        if (amount.scale() > decimalPlaces) {
            throw new InvalidAmountException(amount, "amount scale cannot be greater than " + decimalPlaces);
        }

        var maximumOperationAmount = settings.maximumOperationAmount(currencyId);
        if (amount.compareTo(maximumOperationAmount) > 0) {
            throw new InvalidAmountException(amount, "amount cannot be greater than " + maximumOperationAmount);
        }

        return amount.setScale(decimalPlaces, RoundingMode.UNNECESSARY);
    }

    @Override
    public void validateBalanceAmount(BigDecimal amount) {
        validateBalanceAmount(settings.defaultCurrency().id(), amount);
    }

    @Override
    public void validateBalanceAmount(CurrencyId currencyId, BigDecimal amount) {
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        Objects.requireNonNull(amount, "amount");
        validateCurrencyId(currencyId);

        int decimalPlaces = settings.decimalPlaces(currencyId);

        if (amount.signum() < 0) {
            throw new InvalidAmountException(amount, "balance cannot be negative");
        }

        if (amount.scale() > decimalPlaces) {
            throw new InvalidAmountException(amount, "balance scale cannot be greater than " + decimalPlaces);
        }

        var maximumBalance = settings.maximumBalance(currencyId);
        if (amount.compareTo(maximumBalance) > 0) {
            throw new InvalidAmountException(amount, "balance cannot be greater than " + maximumBalance);
        }
    }

    @Override
    public void validateUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
    }

    @Override
    public void validateCurrencyId(CurrencyId currencyId) {
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        settings.requireEnabledDefinition(currencyId);
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
        validateTransfer(sourceUserId, targetUserId, settings.defaultCurrency().id(), amount);
    }

    @Override
    public void validateTransfer(UUID sourceUserId, UUID targetUserId, CurrencyId currencyId, BigDecimal amount) {
        validateUserId(sourceUserId);
        validateUserId(targetUserId);
        normalizeAmount(currencyId, amount);

        if (sourceUserId.equals(targetUserId)) {
            throw new SameEconomyAccountTransferException(sourceUserId);
        }
    }
}
