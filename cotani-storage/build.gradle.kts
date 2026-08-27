description = "Cotani — storage abstraction and migrations"

dependencies {
    api(project(":task"))
    api(project(":text"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)
    api(libs.hikaricp)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.mariadb.java.client)
    runtimeOnly(libs.sqlite.jdbc)

    testImplementation(libs.jimfs)
    testImplementation(libs.paper.api)
}
