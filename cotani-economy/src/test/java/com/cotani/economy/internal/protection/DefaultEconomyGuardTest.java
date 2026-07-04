package com.cotani.economy.internal.protection;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.exception.InvalidAmountException;
import com.cotani.economy.exception.SameEconomyAccountTransferException;
import java.math.BigDecimal;
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
    void validateCurrencyIdAcceptsValue() {
        assertDoesNotThrow(() -> GUARD.validateCurrencyId(CurrencyId.of("gems")));
    }
}
