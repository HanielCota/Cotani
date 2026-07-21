package com.cotani.economy.internal.service;

import com.cotani.economy.EconomyService;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.internal.protection.EconomyGuard;
import com.cotani.economy.internal.repository.EconomyAccountRepository;
import com.cotani.economy.internal.repository.EconomyTransferRepository;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultEconomyService implements EconomyService {

    private static final Logger LOGGER = Logger.getLogger(DefaultEconomyService.class.getName());

    private final EconomySettings settings;
    private final EconomyGuard guard;
    private final EconomyAccountRepository accountRepository;
    private final EconomyTransferRepository transferRepository;
    private final EconomyEventPublisher eventPublisher;

    public DefaultEconomyService(
            EconomySettings settings,
            EconomyGuard guard,
            EconomyAccountRepository accountRepository,
            EconomyTransferRepository transferRepository,
            EconomyEventPublisher eventPublisher) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.transferRepository = Objects.requireNonNull(transferRepository, "transferRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId) {
        return balance(userId, settings.defaultCurrency().id());
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId) {
        guard.validateUserId(userId);
        guard.validateCurrencyId(currencyId);

        return accountRepository.getOrCreate(userId, currencyId).thenApply(EconomyBalance::from);
    }

    @Override
    public CompletionStage<Boolean> has(UUID userId, BigDecimal amount) {
        return has(userId, settings.defaultCurrency().id(), amount);
    }

    @Override
    public CompletionStage<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount) {
        guard.validateUserId(userId);
        guard.validateCurrencyId(currencyId);

        var normalizedAmount = guard.normalizeAmount(amount);

        return balance(userId, currencyId).thenApply(balance -> balance.amount().compareTo(normalizedAmount) >= 0);
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        guard.validateUserId(userId);
        guard.validateCurrencyId(currencyId);
        guard.validateReason(reason);
        guard.validateOperationId(operationId);

        var normalizedAmount = guard.normalizeAmount(amount);

        return accountRepository
                .deposit(userId, currencyId, normalizedAmount, reason, operationId)
                .thenApply(this::publishAndReturn);
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return deposit(userId, settings.defaultCurrency().id(), amount, reason, operationId);
    }

    @Override
    public CompletionStage<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        guard.validateUserId(userId);
        guard.validateCurrencyId(currencyId);
        guard.validateReason(reason);
        guard.validateOperationId(operationId);

        var normalizedAmount = guard.normalizeAmount(amount);

        return accountRepository
                .withdraw(userId, currencyId, normalizedAmount, reason, operationId)
                .thenApply(this::publishAndReturn);
    }

    @Override
    public CompletionStage<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return withdraw(userId, settings.defaultCurrency().id(), amount, reason, operationId);
    }

    @Override
    public CompletionStage<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        guard.validateUserId(userId);
        guard.validateCurrencyId(currencyId);
        guard.validateReason(reason);
        guard.validateOperationId(operationId);
        guard.validateBalanceAmount(amount);

        var normalizedAmount = amount.setScale(settings.defaultCurrency().decimalPlaces(), RoundingMode.UNNECESSARY);

        return accountRepository
                .set(userId, currencyId, normalizedAmount, reason, operationId)
                .thenApply(this::publishAndReturn);
    }

    @Override
    public CompletionStage<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return set(userId, settings.defaultCurrency().id(), amount, reason, operationId);
    }

    @Override
    public CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        guard.validateTransfer(sourceUserId, targetUserId, amount);
        guard.validateCurrencyId(currencyId);
        guard.validateReason(reason);
        guard.validateOperationId(operationId);

        var normalizedAmount = guard.normalizeAmount(amount);

        return transferRepository
                .transfer(sourceUserId, targetUserId, currencyId, normalizedAmount, reason, operationId)
                .thenApply(this::publishAndReturn);
    }

    @Override
    public CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return transfer(sourceUserId, targetUserId, settings.defaultCurrency().id(), amount, reason, operationId);
    }

    private EconomyTransaction publishAndReturn(EconomyTransaction transaction) {
        try {
            eventPublisher.publish(new EconomyTransactionEvent(transaction));
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to publish economy transaction event", exception);
        }
        return transaction;
    }
}
