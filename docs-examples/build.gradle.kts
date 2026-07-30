description = "Compile-checked Cotani documentation examples"

dependencies {
    implementation(project(":cache"))
    implementation(project(":config"))
    implementation(project(":cooldown"))
    implementation(project(":core"))
    implementation(project(":economy"))
    implementation(project(":event"))
    implementation(project(":metrics"))
    implementation(project(":storage"))
    implementation(project(":task"))
    implementation(project(":teleport"))
    implementation(project(":text"))
    implementation(project(":user"))
    compileOnly(libs.paper.api)
}

tasks.withType<Javadoc>().configureEach {
    enabled = false
}
