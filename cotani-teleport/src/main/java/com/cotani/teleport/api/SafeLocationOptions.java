package com.cotani.teleport.api;

public record SafeLocationOptions(
        int horizontalRadius,
        int verticalRadius,
        boolean avoidLiquids,
        boolean avoidHazards,
        boolean respectWorldBorder) {
    public static SafeLocationOptions defaults() {
        return new SafeLocationOptions(2, 8, true, true, true);
    }
}
