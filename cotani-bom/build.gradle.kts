description = "Cotani — dependency alignment platform"

dependencies {
    constraints {
        api(project(":cache"))
        api(project(":command"))
        api(project(":config"))
        api(project(":cooldown"))
        api(project(":core"))
        api(project(":dialog"))
        api(project(":display"))
        api(project(":economy"))
        api(project(":event"))
        api(project(":gui"))
        api(project(":hud"))
        api(project(":item"))
        api(project(":metrics"))
        api(project(":nametag"))
        api(project(":redis"))
        api(project(":storage"))
        api(project(":task"))
        api(project(":teleport"))
        api(project(":text"))
        api(project(":user"))
    }
}
