description = "Cotani — metrics collection with Micrometer and Prometheus"

dependencies {
    api(project(":config"))
    api(project(":cache"))
    api(project(":storage"))
    api(project(":task"))
    api(libs.jspecify)
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
