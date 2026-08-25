description = "Cotani reward settlement adapters for economy and inventory"

dependencies {
    api(project(":reward"))
    api(project(":economy"))
    api(project(":inventory"))
    api(project(":task"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)
}
