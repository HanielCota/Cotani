description = "Cotani — declarative command module for Paper and Folia"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))
    compileOnly(project(":cooldown"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(project(":cooldown"))
    testImplementation(libs.paper.api)
}
