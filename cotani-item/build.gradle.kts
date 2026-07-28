description = "Cotani — fluent ItemStack builders"

dependencies {
    api(project(":core"))
    api(project(":text"))
    implementation(libs.caffeine)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
