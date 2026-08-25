description = "Cotani - asynchronous seasonal progression and rewards"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(project(":reward"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.sqlite.jdbc)
}
