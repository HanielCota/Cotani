package com.cotani.audit.storage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class AuditDetailsCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private AuditDetailsCodec() {}

    static String encode(Map<String, String> details) {
        var values = new StringBuilder();
        details.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!values.isEmpty()) {
                values.append('|');
            }
            values.append(encodeValue(entry.getKey())).append('.').append(encodeValue(entry.getValue()));
        });
        return values.toString();
    }

    static Map<String, String> decode(String value) {
        if (value.isEmpty()) {
            return Map.of();
        }

        var result = new LinkedHashMap<String, String>();
        for (var pair : value.split("\\|", -1)) {
            var parts = pair.split("\\.", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encoded audit details");
            }
            var key = decodeValue(parts[0]);
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate encoded audit detail key: " + key);
            }
            result.put(key, decodeValue(parts[1]));
        }
        return Map.copyOf(result);
    }

    private static String encodeValue(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeValue(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
}
