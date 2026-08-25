package com.cotani.storage.query;

import org.jspecify.annotations.Nullable;

record Condition(String column, @Nullable Object value) {
    boolean isNullValue() {
        return value == null;
    }

    String sqlClause() {
        return isNullValue() ? column + " IS NULL" : column + " = ?";
    }
}
