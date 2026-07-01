plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — storage abstraction and migrations"

dependencies {
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)
    api(libs.hikaricp)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.mariadb.java.client)
    runtimeOnly(libs.sqlite.jdbc)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
