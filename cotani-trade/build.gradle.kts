description = "Cotani - asynchronous player-to-player trades"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(project(":economy"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
}
