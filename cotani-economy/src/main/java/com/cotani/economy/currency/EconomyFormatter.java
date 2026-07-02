package com.cotani.economy.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public final class EconomyFormatter {

    private final EconomyCurrency currency;
    private final DecimalFormat decimalFormat;

    public EconomyFormatter(EconomyCurrency currency, Locale locale) {
        this.currency = Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(locale, "locale");

        var symbols = DecimalFormatSymbols.getInstance(locale);
        var pattern = currency.decimalPlaces() == 0 ? "#,##0" : "#,##0." + "0".repeat(currency.decimalPlaces());

        decimalFormat = new DecimalFormat(pattern, symbols);
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        decimalFormat.setMinimumFractionDigits(currency.decimalPlaces());
        decimalFormat.setMaximumFractionDigits(currency.decimalPlaces());
    }

    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        return currency.symbol() + decimalFormat.format(amount);
    }
}
