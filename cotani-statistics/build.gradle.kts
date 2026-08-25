description = "Cotani - asynchronous persistent player statistics"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.sqlite.jdbc)
}
