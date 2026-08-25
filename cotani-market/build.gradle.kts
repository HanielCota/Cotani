description = "Cotani - asynchronous player marketplace"

dependencies {
    api(project(":core"))
    api(project(":economy"))
    api(project(":event"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
}
