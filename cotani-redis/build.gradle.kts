description = "Cotani — non-blocking Redis client, pub/sub messaging, and distributed locks"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":config"))

    api(libs.jspecify)
    api(libs.lettuce.core)

    compileOnlyApi(libs.paper.api)
    compileOnlyApi(project(":cache"))
    compileOnlyApi(project(":cooldown"))
    compileOnlyApi(project(":event"))

    testImplementation(libs.paper.api)
    testImplementation(project(":cache"))
    testImplementation(project(":cooldown"))
    testImplementation(project(":event"))
    testImplementation(libs.testcontainers.junit.jupiter)
}
