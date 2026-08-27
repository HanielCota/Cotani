description = "Cotani reward settlement adapters for economy and inventory"

dependencies {
    api(project(":reward"))
    api(project(":economy"))
    api(project(":inventory"))
    api(project(":task"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)
    testImplementation(libs.paper.api)
    testImplementation(project(":achievement"))
    testImplementation(project(":event"))
    testImplementation(project(":quest"))
    testImplementation(project(":ranking"))
    testImplementation(project(":season"))
    testImplementation(project(":statistics"))
}
