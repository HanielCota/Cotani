description = "Cotani - asynchronous persistent player mail"

dependencies {
    api(project(":core"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
}
