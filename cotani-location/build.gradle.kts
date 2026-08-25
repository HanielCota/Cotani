description = "Cotani - asynchronous homes and warps service"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":teleport"))
    api(project(":storage"))
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
