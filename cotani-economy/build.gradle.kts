description = "Cotani - economy module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":storage"))
    api(project(":config"))
    api(project(":text"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.mariadb)
    testRuntimeOnly(libs.mysql.connector)
    testRuntimeOnly(libs.mariadb.java.client)
}
