package com.cotani.storage.query;

record Condition(String column, Object value) {
    boolean isNullValue() {
        return value == null;
    }

    String sqlClause() {
        return isNullValue() ? column + " IS NULL" : column + " = ?";
    }
}
