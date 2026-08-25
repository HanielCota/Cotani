description = "Cotani - asynchronous party and group service"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
}
