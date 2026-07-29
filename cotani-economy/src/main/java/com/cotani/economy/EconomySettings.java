package com.cotani.economy;

import com.cotani.economy.currency.CurrencyDefinition;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record EconomySettings(
        EconomyCurrency defaultCurrency,
        Map<CurrencyId, EconomyCurrency> currencies,
        Map<CurrencyId, CurrencyDefinition> currencyDefinitions,
        BigDecimal startingBalance,
        BigDecimal maximumBalance,
        BigDecimal maximumOperationAmount,
        BigDecimal minimumPayAmount,
        int balanceCacheSeconds,
        int topCacheSeconds) {

    public EconomySettings {
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(currencyDefinitions, "currencyDefinitions");
        Objects.requireNonNull(startingBalance, "startingBalance");
        Objects.requireNonNull(maximumBalance, "maximumBalance");
        Objects.requireNonNull(maximumOperationAmount, "maximumOperationAmount");
        Objects.requireNonNull(minimumPayAmount, "minimumPayAmount");

        var resolved = new LinkedHashMap<CurrencyId, EconomyCurrency>();
        resolved.put(defaultCurrency.id(), defaultCurrency);
        for (EconomyCurrency currency : currencies.values()) {
            Objects.requireNonNull(currency, "currency");
            resolved.putIfAbsent(currency.id(), currency);
        }
        currencies = Map.copyOf(resolved);

        if (startingBalance.signum() < 0) {
            throw new IllegalArgumentException("startingBalance cannot be negative.");
        }

        if (maximumBalance.signum() <= 0) {
            throw new IllegalArgumentException("maximumBalance must be positive.");
        }

        if (maximumOperationAmount.signum() <= 0) {
            throw new IllegalArgumentException("maximumOperationAmount must be positive.");
        }

        if (minimumPayAmount.signum() < 0) {
            throw new IllegalArgumentException("minimumPayAmount cannot be negative.");
        }

        if (startingBalance.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("startingBalance cannot be greater than maximumBalance.");
        }

        if (maximumOperationAmount.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("maximumOperationAmount cannot be greater than maximumBalance.");
        }

        if (minimumPayAmount.compareTo(maximumOperationAmount) > 0) {
            throw new IllegalArgumentException("minimumPayAmount cannot be greater than maximumOperationAmount.");
        }

        if (balanceCacheSeconds < 0) {
            throw new IllegalArgumentException("balanceCacheSeconds cannot be negative.");
        }

        if (topCacheSeconds < 0) {
            throw new IllegalArgumentException("topCacheSeconds cannot be negative.");
        }

        for (var configuredId : currencyDefinitions.keySet()) {
            if (!currencies.containsKey(configuredId)) {
                throw new IllegalArgumentException(
                        "Currency definition has no registered currency: " + configuredId.value());
            }
        }
        var resolvedDefinitions = new LinkedHashMap<CurrencyId, CurrencyDefinition>();
        for (EconomyCurrency currency : currencies.values()) {
            var definition = currencyDefinitions.getOrDefault(
                    currency.id(),
                    new CurrencyDefinition(
                            currency, startingBalance, maximumBalance, maximumOperationAmount, minimumPayAmount, true));
            if (!definition.currency().equals(currency)) {
                throw new IllegalArgumentException("Currency definition does not match registered currency: "
                        + currency.id().value());
            }
            requireScale(definition.startingBalance(), currency, "startingBalance");
            requireScale(definition.maximumBalance(), currency, "maximumBalance");
            requireScale(definition.maximumOperationAmount(), currency, "maximumOperationAmount");
            requireScale(definition.minimumPayAmount(), currency, "minimumPayAmount");
            resolvedDefinitions.put(currency.id(), definition);
        }
        currencyDefinitions = Map.copyOf(resolvedDefinitions);
        if (!Objects.requireNonNull(currencyDefinitions.get(defaultCurrency.id()), "defaultCurrencyDefinition")
                .enabled()) {
            throw new IllegalArgumentException("The default currency must be enabled.");
        }
    }

    /**
     * Legacy local-cache setting retained for source compatibility.
     *
     * @deprecated balance reads are strongly consistent and no longer cached by the module
     *     bootstrap
     */
    @Override
    @Deprecated(forRemoval = false)
    public int balanceCacheSeconds() {
        return balanceCacheSeconds;
    }

    /** Backward-compatible constructor using global limits for every registered currency. */
    public EconomySettings(
            EconomyCurrency defaultCurrency,
            Map<CurrencyId, EconomyCurrency> currencies,
            BigDecimal startingBalance,
            BigDecimal maximumBalance,
            BigDecimal maximumOperationAmount,
            BigDecimal minimumPayAmount,
            int balanceCacheSeconds,
            int topCacheSeconds) {
        this(
                defaultCurrency,
                currencies,
                Map.of(),
                startingBalance,
                maximumBalance,
                maximumOperationAmount,
                minimumPayAmount,
                balanceCacheSeconds,
                topCacheSeconds);
    }

    /**
     * Backward-compatible constructor for a single default currency registry.
     */
    public EconomySettings(
            EconomyCurrency defaultCurrency,
            BigDecimal startingBalance,
            BigDecimal maximumBalance,
            BigDecimal maximumOperationAmount,
            BigDecimal minimumPayAmount,
            int balanceCacheSeconds,
            int topCacheSeconds) {
        this(
                defaultCurrency,
                Map.of(defaultCurrency.id(), defaultCurrency),
                startingBalance,
                maximumBalance,
                maximumOperationAmount,
                minimumPayAmount,
                balanceCacheSeconds,
                topCacheSeconds);
    }

    public static EconomySettings defaultSettings(EconomyCurrency currency) {
        Objects.requireNonNull(currency, "currency");

        return new EconomySettings(
                currency,
                Map.of(currency.id(), currency),
                BigDecimal.ZERO.setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY),
                new BigDecimal("1000000000000").setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY),
                new BigDecimal("100000000").setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY),
                BigDecimal.ONE.setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY),
                30,
                60);
    }

    public static EconomySettings defaultSettings(EconomyCurrency defaultCurrency, Collection<EconomyCurrency> extra) {
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        Objects.requireNonNull(extra, "extra");

        var map = new LinkedHashMap<CurrencyId, EconomyCurrency>();
        map.put(defaultCurrency.id(), defaultCurrency);
        for (EconomyCurrency currency : extra) {
            map.put(currency.id(), currency);
        }
        return new EconomySettings(
                defaultCurrency,
                map,
                BigDecimal.ZERO.setScale(defaultCurrency.decimalPlaces(), RoundingMode.UNNECESSARY),
                new BigDecimal("1000000000000").setScale(defaultCurrency.decimalPlaces(), RoundingMode.UNNECESSARY),
                new BigDecimal("100000000").setScale(defaultCurrency.decimalPlaces(), RoundingMode.UNNECESSARY),
                BigDecimal.ONE.setScale(defaultCurrency.decimalPlaces(), RoundingMode.UNNECESSARY),
                30,
                60);
    }

    public Optional<EconomyCurrency> findCurrency(CurrencyId currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        return Optional.ofNullable(currencies.get(currencyId));
    }

    public EconomyCurrency requireCurrency(CurrencyId currencyId) {
        return findCurrency(currencyId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown currency: " + currencyId.value()));
    }

    public int decimalPlaces(CurrencyId currencyId) {
        return requireEnabledDefinition(currencyId).currency().decimalPlaces();
    }

    public BigDecimal startingBalance(CurrencyId currencyId) {
        return scaleFor(currencyId, requireEnabledDefinition(currencyId).startingBalance());
    }

    public BigDecimal maximumBalance(CurrencyId currencyId) {
        return scaleFor(currencyId, requireEnabledDefinition(currencyId).maximumBalance());
    }

    public BigDecimal maximumOperationAmount(CurrencyId currencyId) {
        return scaleFor(currencyId, requireEnabledDefinition(currencyId).maximumOperationAmount());
    }

    public BigDecimal minimumPayAmount(CurrencyId currencyId) {
        return scaleFor(currencyId, requireEnabledDefinition(currencyId).minimumPayAmount());
    }

    public CurrencyDefinition requireEnabledDefinition(CurrencyId currencyId) {
        requireCurrency(currencyId);
        var definition = Objects.requireNonNull(currencyDefinitions.get(currencyId), "currencyDefinition");
        if (!definition.enabled()) {
            throw new IllegalArgumentException("Currency is disabled: " + currencyId.value());
        }
        return definition;
    }

    private BigDecimal scaleFor(CurrencyId currencyId, BigDecimal value) {
        return value.setScale(decimalPlaces(currencyId), RoundingMode.UNNECESSARY);
    }

    private static void requireScale(BigDecimal value, EconomyCurrency currency, String settingName) {
        try {
            var _ = value.setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalidScale) {
            throw new IllegalArgumentException(
                    settingName + " is not representable by currency "
                            + currency.id().value() + " with " + currency.decimalPlaces() + " decimal places.",
                    invalidScale);
        }
    }
}
