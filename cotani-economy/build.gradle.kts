plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — economy module"

dependencies {
    api(project(":core"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
