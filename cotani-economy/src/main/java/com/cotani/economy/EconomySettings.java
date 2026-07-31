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
    private static final String STARTING_BALANCE_SETTING = "startingBalance";
    private static final String MAXIMUM_BALANCE_SETTING = "maximumBalance";
    private static final String MAXIMUM_OPERATION_AMOUNT_SETTING = "maximumOperationAmount";
    private static final String MINIMUM_PAY_AMOUNT_SETTING = "minimumPayAmount";

    public EconomySettings {
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(currencyDefinitions, "currencyDefinitions");
        Objects.requireNonNull(startingBalance, STARTING_BALANCE_SETTING);
        Objects.requireNonNull(maximumBalance, MAXIMUM_BALANCE_SETTING);
        Objects.requireNonNull(maximumOperationAmount, MAXIMUM_OPERATION_AMOUNT_SETTING);
        Objects.requireNonNull(minimumPayAmount, MINIMUM_PAY_AMOUNT_SETTING);

        currencies = resolveCurrencies(defaultCurrency, currencies);
        validateGlobalLimits(startingBalance, maximumBalance, maximumOperationAmount, minimumPayAmount);
        validateCacheDurations(balanceCacheSeconds, topCacheSeconds);
        currencyDefinitions = resolveDefinitions(
                currencies,
                currencyDefinitions,
                startingBalance,
                maximumBalance,
                maximumOperationAmount,
                minimumPayAmount);
        requireEnabledDefaultCurrency(defaultCurrency, currencyDefinitions);
    }

    /**
     * Legacy local-cache setting retained for source compatibility.
     *
     * @deprecated balance reads are strongly consistent and no longer cached by the module
     *     bootstrap
     */
    @Override
    @Deprecated(forRemoval = false)
    @SuppressWarnings("java:S1133") // Retained intentionally for source compatibility.
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

    private static Map<CurrencyId, EconomyCurrency> resolveCurrencies(
            EconomyCurrency defaultCurrency, Map<CurrencyId, EconomyCurrency> configuredCurrencies) {
        var resolved = new LinkedHashMap<CurrencyId, EconomyCurrency>();
        resolved.put(defaultCurrency.id(), defaultCurrency);
        for (EconomyCurrency currency : configuredCurrencies.values()) {
            var requiredCurrency = Objects.requireNonNull(currency, "currency");
            resolved.putIfAbsent(requiredCurrency.id(), requiredCurrency);
        }
        return Map.copyOf(resolved);
    }

    private static void validateGlobalLimits(
            BigDecimal startingBalance,
            BigDecimal maximumBalance,
            BigDecimal maximumOperationAmount,
            BigDecimal minimumPayAmount) {
        requireNonNegative(startingBalance, STARTING_BALANCE_SETTING);
        requirePositive(maximumBalance, MAXIMUM_BALANCE_SETTING);
        requirePositive(maximumOperationAmount, MAXIMUM_OPERATION_AMOUNT_SETTING);
        requireNonNegative(minimumPayAmount, MINIMUM_PAY_AMOUNT_SETTING);
        requireNotGreater(startingBalance, maximumBalance, STARTING_BALANCE_SETTING, MAXIMUM_BALANCE_SETTING);
        requireNotGreater(
                maximumOperationAmount, maximumBalance, MAXIMUM_OPERATION_AMOUNT_SETTING, MAXIMUM_BALANCE_SETTING);
        requireNotGreater(
                minimumPayAmount, maximumOperationAmount, MINIMUM_PAY_AMOUNT_SETTING, MAXIMUM_OPERATION_AMOUNT_SETTING);
    }

    private static void validateCacheDurations(int balanceCacheSeconds, int topCacheSeconds) {
        if (balanceCacheSeconds < 0) {
            throw new IllegalArgumentException("balanceCacheSeconds cannot be negative.");
        }
        if (topCacheSeconds < 0) {
            throw new IllegalArgumentException("topCacheSeconds cannot be negative.");
        }
    }

    private static Map<CurrencyId, CurrencyDefinition> resolveDefinitions(
            Map<CurrencyId, EconomyCurrency> currencies,
            Map<CurrencyId, CurrencyDefinition> configuredDefinitions,
            BigDecimal startingBalance,
            BigDecimal maximumBalance,
            BigDecimal maximumOperationAmount,
            BigDecimal minimumPayAmount) {
        validateDefinitionKeys(currencies, configuredDefinitions);
        var resolved = new LinkedHashMap<CurrencyId, CurrencyDefinition>();

        for (EconomyCurrency currency : currencies.values()) {
            var fallback = new CurrencyDefinition(
                    currency, startingBalance, maximumBalance, maximumOperationAmount, minimumPayAmount, true);
            var definition = Objects.requireNonNull(
                    configuredDefinitions.getOrDefault(currency.id(), fallback), "currencyDefinition");
            validateDefinition(currency, definition);
            resolved.put(currency.id(), definition);
        }
        return Map.copyOf(resolved);
    }

    private static void validateDefinitionKeys(
            Map<CurrencyId, EconomyCurrency> currencies, Map<CurrencyId, CurrencyDefinition> configuredDefinitions) {
        for (var configuredId : configuredDefinitions.keySet()) {
            var requiredId = Objects.requireNonNull(configuredId, "configuredCurrencyId");

            if (!currencies.containsKey(requiredId)) {
                throw new IllegalArgumentException(
                        "Currency definition has no registered currency: " + requiredId.value());
            }
        }
    }

    private static void validateDefinition(EconomyCurrency currency, CurrencyDefinition definition) {
        if (!definition.currency().equals(currency)) {
            throw new IllegalArgumentException("Currency definition does not match registered currency: "
                    + currency.id().value());
        }

        requireScale(definition.startingBalance(), currency, STARTING_BALANCE_SETTING);
        requireScale(definition.maximumBalance(), currency, MAXIMUM_BALANCE_SETTING);
        requireScale(definition.maximumOperationAmount(), currency, MAXIMUM_OPERATION_AMOUNT_SETTING);
        requireScale(definition.minimumPayAmount(), currency, MINIMUM_PAY_AMOUNT_SETTING);
    }

    private static void requireEnabledDefaultCurrency(
            EconomyCurrency defaultCurrency, Map<CurrencyId, CurrencyDefinition> definitions) {
        if (!Objects.requireNonNull(definitions.get(defaultCurrency.id()), "defaultCurrencyDefinition")
                .enabled()) {
            throw new IllegalArgumentException("The default currency must be enabled.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String settingName) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(settingName + " cannot be negative.");
        }
    }

    private static void requirePositive(BigDecimal value, String settingName) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(settingName + " must be positive.");
        }
    }

    private static void requireNotGreater(BigDecimal value, BigDecimal limit, String settingName, String limitName) {
        if (value.compareTo(limit) > 0) {
            throw new IllegalArgumentException(settingName + " cannot be greater than " + limitName + ".");
        }
    }
}
