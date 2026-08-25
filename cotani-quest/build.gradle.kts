description = "Cotani - asynchronous objective-based player quests"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(project(":reward"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.sqlite.jdbc)
}
