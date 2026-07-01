plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — lifecycle and contracts"

dependencies {
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

