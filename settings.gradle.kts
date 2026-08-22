pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "cotani"

include(
    "bom",
    "cache",
    "config",
    "core",
    "dialog",
    "economy",
    "text",
    "item",
    "task",
    "teleport",
    "storage",
    "user",
    "cooldown",
    "event",
    "metrics",
    "gui",
    "display",
    "command",
    "hud",
    "nametag",
    "redis",
    "examples"
)

project(":bom").projectDir = file("cotani-bom")
project(":cache").projectDir = file("cotani-cache")
project(":config").projectDir = file("cotani-config")
project(":core").projectDir = file("cotani-core")
project(":dialog").projectDir = file("cotani-dialog")
project(":economy").projectDir = file("cotani-economy")
project(":text").projectDir = file("cotani-text")
project(":item").projectDir = file("cotani-item")
project(":task").projectDir = file("cotani-task")
project(":teleport").projectDir = file("cotani-teleport")
project(":storage").projectDir = file("cotani-storage")
project(":user").projectDir = file("cotani-user")
project(":cooldown").projectDir = file("cotani-cooldown")
project(":event").projectDir = file("cotani-event")
project(":metrics").projectDir = file("cotani-metrics")
project(":gui").projectDir = file("cotani-gui")
project(":display").projectDir = file("cotani-display")
project(":command").projectDir = file("cotani-command")
project(":hud").projectDir = file("cotani-hud")
project(":nametag").projectDir = file("cotani-nametag")
project(":redis").projectDir = file("cotani-redis")
project(":examples").projectDir = file("docs-examples")


