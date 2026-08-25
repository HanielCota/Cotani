description = "Cotani - asynchronous friendships, requests and player blocks"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
}
