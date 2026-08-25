description = "Cotani - asynchronous priority queues and matchmaking"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
}
