plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — MiniMessage, placeholders and Adventure audience utilities"

dependencies {
    api(project(":core"))

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

