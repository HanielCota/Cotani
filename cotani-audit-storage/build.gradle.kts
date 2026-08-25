description = "Cotani - SQL adapter for the audit trail"

dependencies {
    api(project(":audit"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
