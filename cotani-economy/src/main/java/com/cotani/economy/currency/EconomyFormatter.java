package com.cotani.economy.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public final class EconomyFormatter {

    private final EconomyCurrency currency;
    private final Locale locale;
    private final ThreadLocal<DecimalFormat> formatCache;

    public EconomyFormatter(EconomyCurrency currency, Locale locale) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.formatCache = ThreadLocal.withInitial(this::createFormat);
    }

    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        return currency.symbol() + formatCache.get().format(amount);
    }

    private DecimalFormat createFormat() {
        var symbols = DecimalFormatSymbols.getInstance(locale);
        var pattern = currency.decimalPlaces() == 0 ? "#,##0" : "#,##0." + "0".repeat(currency.decimalPlaces());

        var decimalFormat = new DecimalFormat(pattern, symbols);
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        decimalFormat.setMinimumFractionDigits(currency.decimalPlaces());
        decimalFormat.setMaximumFractionDigits(currency.decimalPlaces());
        return decimalFormat;
    }
}
