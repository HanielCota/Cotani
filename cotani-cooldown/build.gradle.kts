import net.ltgt.gradle.errorprone.errorprone

description = "Cotani - basic cooldown module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":cache"))
    api(project(":storage"))
    api(project(":config"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.mariadb)
    testRuntimeOnly(libs.mysql.connector)
    testRuntimeOnly(libs.mariadb.java.client)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        excludedPaths.set(".*/CacheCooldownStore\\.java")
    }
}
