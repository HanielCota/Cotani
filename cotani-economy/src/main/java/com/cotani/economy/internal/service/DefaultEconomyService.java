package com.cotani.economy.internal.service;

import com.cotani.api.InternalApi;
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
import com.cotani.task.util.VoidResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

@InternalApi
public final class DefaultEconomyService implements EconomyService {
    private static final Logger LOGGER = Logger.getLogger(DefaultEconomyService.class.getName());
    private static final String FAILED_PUBLISH_MSG = "Failed to publish economy transaction event";
    private static final int MAX_PUBLISHED_OPERATIONS_CACHE = 50_000;
    private static final Duration PUBLISHED_OPERATIONS_EXPIRY = Duration.ofMinutes(15);

    private final EconomySettings settings;
    private final EconomyGuard guard;
    private final EconomyAccountRepository accountRepository;
    private final EconomyTransferRepository transferRepository;
    private final EconomyEventPublisher eventPublisher;
    private final Cache<EconomyOperationId, Boolean> publishedOperations = Caffeine.newBuilder()
            .maximumSize(MAX_PUBLISHED_OPERATIONS_CACHE)
            .expireAfterWrite(PUBLISHED_OPERATIONS_EXPIRY)
            .build();

    private DefaultEconomyService(
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

    public static DefaultEconomyService create(
            EconomySettings settings,
            EconomyGuard guard,
            EconomyAccountRepository accountRepository,
            EconomyTransferRepository transferRepository,
            EconomyEventPublisher eventPublisher) {
        return new DefaultEconomyService(settings, guard, accountRepository, transferRepository, eventPublisher);
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId) {
        return balance(userId, settings.defaultCurrency().id());
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId) {
        return runGuarded(
                () -> {
                    guard.validateUserId(userId);
                    guard.validateCurrencyId(currencyId);
                    return userId;
                },
                ignored -> accountRepository.getOrCreate(userId, currencyId).thenApply(EconomyBalance::from));
    }

    @Override
    public CompletionStage<Boolean> has(UUID userId, BigDecimal amount) {
        return has(userId, settings.defaultCurrency().id(), amount);
    }

    @Override
    public CompletionStage<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount) {
        return runGuarded(
                () -> {
                    guard.validateUserId(userId);
                    guard.validateCurrencyId(currencyId);
                    return guard.normalizeAmount(currencyId, amount);
                },
                normalizedAmount -> balance(userId, currencyId)
                        .thenApply(balance -> balance.amount().compareTo(normalizedAmount) >= 0));
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return runGuarded(
                () -> {
                    guard.validateUserId(userId);
                    guard.validateCurrencyId(currencyId);
                    guard.validateReason(reason);
                    guard.validateOperationId(operationId);
                    return guard.normalizeAmount(currencyId, amount);
                },
                normalizedAmount -> accountRepository
                        .deposit(userId, currencyId, normalizedAmount, reason, operationId)
                        .thenCompose(this::publishAndReturn));
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
        return runGuarded(
                () -> {
                    guard.validateUserId(userId);
                    guard.validateCurrencyId(currencyId);
                    guard.validateReason(reason);
                    guard.validateOperationId(operationId);
                    return guard.normalizeAmount(currencyId, amount);
                },
                normalizedAmount -> accountRepository
                        .withdraw(userId, currencyId, normalizedAmount, reason, operationId)
                        .thenCompose(this::publishAndReturn));
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
        return runGuarded(
                () -> {
                    guard.validateUserId(userId);
                    guard.validateCurrencyId(currencyId);
                    guard.validateReason(reason);
                    guard.validateOperationId(operationId);
                    guard.validateBalanceAmount(currencyId, amount);
                    return amount.setScale(settings.decimalPlaces(currencyId), RoundingMode.UNNECESSARY);
                },
                normalizedAmount -> accountRepository
                        .set(userId, currencyId, normalizedAmount, reason, operationId)
                        .thenCompose(this::publishAndReturn));
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
        return runGuarded(
                () -> {
                    guard.validateTransfer(sourceUserId, targetUserId, currencyId, amount);
                    guard.validateCurrencyId(currencyId);
                    guard.validateReason(reason);
                    guard.validateOperationId(operationId);
                    return guard.normalizeAmount(currencyId, amount);
                },
                normalizedAmount -> transferRepository
                        .transfer(sourceUserId, targetUserId, currencyId, normalizedAmount, reason, operationId)
                        .thenCompose(this::publishAndReturn));
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

    private CompletionStage<EconomyTransaction> publishAndReturn(EconomyTransaction transaction) {
        if (publishedOperations.asMap().putIfAbsent(transaction.operationId(), Boolean.TRUE) != null) {
            return CompletableFuture.completedFuture(transaction);
        }
        try {
            return eventPublisher
                    .publishAsync(new EconomyTransactionEvent(transaction))
                    .exceptionally(error -> {
                        LOGGER.log(Level.WARNING, FAILED_PUBLISH_MSG, error);
                        return VoidResult.nullValue();
                    })
                    .thenApply(_ -> transaction);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, FAILED_PUBLISH_MSG, exception);
            return CompletableFuture.completedFuture(transaction);
        }
    }

    private <I, O> CompletionStage<O> runGuarded(
            java.util.function.Supplier<I> validation, java.util.function.Function<I, CompletionStage<O>> action) {
        try {
            return action.apply(validation.get());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
