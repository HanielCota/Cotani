package com.cotani.reward.storage;

import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.ItemGrant;
import com.cotani.reward.api.RewardGrant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

final class RewardGrantCodec {
    private static final String VERSION = "1";

    private RewardGrantCodec() {}

    static String encode(List<RewardGrant> grants) {
        return VERSION + ";"
                + grants.stream()
                        .map(RewardGrantCodec::encodeGrant)
                        .reduce((left, right) -> left + ";" + right)
                        .orElseThrow();
    }

    static List<RewardGrant> decode(String encoded) {
        var parts = encoded.split(";", -1);
        if (parts.length < 2 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("Unsupported reward grant payload version");
        }
        var grants = new java.util.ArrayList<RewardGrant>(parts.length - 1);
        for (var index = 1; index < parts.length; index++) {
            grants.add(decodeGrant(parts[index]));
        }
        return List.copyOf(grants);
    }

    private static String encodeGrant(RewardGrant grant) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return switch (grant) {
            case CurrencyGrant currency ->
                "C|" + encoder.encodeToString(currency.currency().getBytes(StandardCharsets.UTF_8)) + "|"
                        + currency.amount().toPlainString();
            case ItemGrant item ->
                "I|" + encoder.encodeToString(item.itemKey().getBytes(StandardCharsets.UTF_8)) + "|" + item.amount();
        };
    }

    private static RewardGrant decodeGrant(String encoded) {
        var parts = encoded.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalStateException("Malformed reward grant payload");
        }
        var name = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        try {
            return switch (parts[0]) {
                case "C" -> new CurrencyGrant(name, new BigDecimal(parts[2]));
                case "I" -> new ItemGrant(name, Integer.parseInt(parts[2]));
                default -> throw new IllegalStateException("Unknown reward grant type: " + parts[0]);
            };
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Malformed reward grant value", exception);
        }
    }
}
