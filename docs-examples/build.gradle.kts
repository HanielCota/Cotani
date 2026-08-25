description = "Compile-checked Cotani documentation examples"

dependencies {
    implementation(project(":cache"))
    implementation(project(":command"))
    implementation(project(":config"))
    implementation(project(":cooldown"))
    implementation(project(":core"))
    implementation(project(":dialog"))
    implementation(project(":display"))
    implementation(project(":economy"))
    implementation(project(":event"))
    implementation(project(":gui"))
    implementation(project(":hud"))
    implementation(project(":item"))
    implementation(project(":metrics"))
    implementation(project(":nametag"))
    implementation(project(":npc"))
    implementation(project(":region"))
    implementation(project(":inventory"))
    implementation(project(":redis"))
    implementation(project(":reward"))
    implementation(project(":reward-integration"))
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
