plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — basic cooldown module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":cache"))
    api(project(":storage"))
    api(project(":config"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
