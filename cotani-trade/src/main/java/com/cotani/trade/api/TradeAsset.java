package com.cotani.trade.api;

import java.nio.charset.StandardCharsets;

/** Immutable asset offered by one participant. */
public sealed interface TradeAsset permits TradeItem, TradeCurrency {
    /** Stable asset type used by the settlement adapter. */
    String assetType();

    /** Stable asset identity used by the settlement adapter. */
    String assetKey();

    /** Approximate UTF-8 size used to protect persistence and transport boundaries. */
    long encodedSizeBytes();

    /** Returns the UTF-8 size of a stable value. */
    static int utf8Size(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
