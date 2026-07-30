package com.cotani.economy.internal.protection;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.CurrencyDefinition;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.exception.InvalidAmountException;
import com.cotani.economy.exception.SameEconomyAccountTransferException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultEconomyGuardTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final DefaultEconomyGuard GUARD = new DefaultEconomyGuard(SETTINGS);

    @Test
    void normalizeAmountAcceptsValidAmount() {
        var amount = BigDecimal.valueOf(10);

        var normalized = GUARD.normalizeAmount(amount);

        assertEquals(0, normalized.compareTo(new BigDecimal("10.00")));
        assertEquals(2, normalized.scale());
    }

    @Test
    void normalizeAmountRejectsZero() {
        assertThrows(InvalidAmountException.class, () -> GUARD.normalizeAmount(BigDecimal.ZERO));
    }

    @Test
    void normalizeAmountRejectsNegative() {
        assertThrows(InvalidAmountException.class, () -> GUARD.normalizeAmount(BigDecimal.valueOf(-5)));
    }

    @Test
    void normalizeAmountRejectsScaleGreaterThanCurrency() {
        assertThrows(InvalidAmountException.class, () -> GUARD.normalizeAmount(new BigDecimal("1.001")));
    }

    @Test
    void normalizeAmountRejectsAmountAboveMaximumOperation() {
        var tooLarge = SETTINGS.maximumOperationAmount().add(BigDecimal.ONE);

        assertThrows(InvalidAmountException.class, () -> GUARD.normalizeAmount(tooLarge));
    }

    @Test
    void validateBalanceAmountAcceptsValidBalance() {
        assertDoesNotThrow(() -> GUARD.validateBalanceAmount(BigDecimal.valueOf(100)));
    }

    @Test
    void validateBalanceAmountRejectsNegative() {
        assertThrows(InvalidAmountException.class, () -> GUARD.validateBalanceAmount(BigDecimal.valueOf(-1)));
    }

    @Test
    void validateBalanceAmountRejectsAboveMaximum() {
        var tooLarge = SETTINGS.maximumBalance().add(BigDecimal.ONE);

        assertThrows(InvalidAmountException.class, () -> GUARD.validateBalanceAmount(tooLarge));
    }

    @Test
    void validateTransferRejectsSameAccount() {
        var userId = UUID.randomUUID();

        assertThrows(
                SameEconomyAccountTransferException.class,
                () -> GUARD.validateTransfer(userId, userId, BigDecimal.TEN));
    }

    @Test
    void validateTransferAcceptsDifferentAccounts() {
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();

        assertDoesNotThrow(() -> GUARD.validateTransfer(source, target, BigDecimal.TEN));
    }

    @Test
    @SuppressWarnings("NullAway")
    void validateCurrencyIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> GUARD.validateCurrencyId(null));
    }

    @Test
    void validateCurrencyIdAcceptsDefaultCurrency() {
        assertDoesNotThrow(
                () -> GUARD.validateCurrencyId(SETTINGS.defaultCurrency().id()));
    }

    @Test
    void validateCurrencyIdRejectsUnknownCurrency() {
        assertThrows(IllegalArgumentException.class, () -> GUARD.validateCurrencyId(CurrencyId.of("gems")));
    }

    @Test
    void normalizeAmountUsesCurrencySpecificScale() {
        var gems = new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0);
        var tokens = new EconomyCurrency(CurrencyId.of("tokens"), "Tokens", "T", 4);
        var coins = EconomyCurrency.coins();
        var settings = EconomySettings.defaultSettings(coins, List.of(gems, tokens));
        var guard = new DefaultEconomyGuard(settings);

        var normalizedGems = guard.normalizeAmount(gems.id(), BigDecimal.TEN);
        assertEquals(0, normalizedGems.scale());

        assertThrows(InvalidAmountException.class, () -> guard.normalizeAmount(gems.id(), new BigDecimal("1.5")));
        assertDoesNotThrow(() -> guard.normalizeAmount(coins.id(), new BigDecimal("1.50")));
        assertEquals(
                4, guard.normalizeAmount(tokens.id(), new BigDecimal("1.2345")).scale());
        assertEquals(0, settings.startingBalance(gems.id()).scale());
        assertEquals(4, settings.startingBalance(tokens.id()).scale());
    }

    @Test
    void currencyDefinitionsApplyIndependentLimitsAndDisabledState() {
        var coins = EconomyCurrency.coins();
        var gems = new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0);
        var tokens = new EconomyCurrency(CurrencyId.of("tokens"), "Tokens", "T", 4);
        var disabled = new EconomyCurrency(CurrencyId.of("legacy"), "Legacy", "L", 2);
        var settings = new EconomySettings(
                coins,
                Map.of(coins.id(), coins, gems.id(), gems, tokens.id(), tokens, disabled.id(), disabled),
                Map.of(
                        gems.id(),
                        new CurrencyDefinition(
                                gems,
                                BigDecimal.ZERO,
                                new BigDecimal("100"),
                                new BigDecimal("10"),
                                BigDecimal.ONE,
                                true),
                        tokens.id(),
                        new CurrencyDefinition(
                                tokens,
                                new BigDecimal("0.0000"),
                                new BigDecimal("5.0000"),
                                new BigDecimal("1.0000"),
                                new BigDecimal("0.0001"),
                                true),
                        disabled.id(),
                        new CurrencyDefinition(
                                disabled,
                                new BigDecimal("0.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("1.00"),
                                new BigDecimal("0.01"),
                                false)),
                new BigDecimal("0.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("1.00"),
                30,
                60);
        var guard = new DefaultEconomyGuard(settings);

        assertEquals(0, guard.normalizeAmount(gems.id(), BigDecimal.TEN).scale());
        assertThrows(InvalidAmountException.class, () -> guard.normalizeAmount(gems.id(), new BigDecimal("11")));
        assertEquals(
                4, guard.normalizeAmount(tokens.id(), new BigDecimal("0.1234")).scale());
        assertThrows(InvalidAmountException.class, () -> guard.normalizeAmount(tokens.id(), new BigDecimal("1.0001")));
        assertThrows(IllegalArgumentException.class, () -> guard.validateCurrencyId(disabled.id()));
    }
}
