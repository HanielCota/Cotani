description = "Cotani - immutable asynchronous player punishments"

dependencies {
    api(project(":core"))
    api(project(":audit"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
}
