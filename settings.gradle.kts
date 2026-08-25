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
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

rootProject.name = "cotani"

include(
    "bom",
    "audit",
    "audit-storage",
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
    "npc",
    "region",
    "redis",
    "placeholder",
    "permission",
    "inventory",
    "locale",
    "party",
    "friend",
    "queue",
    "trade",
    "punishment",
    "location",
    "mail",
    "reward",
    "reward-integration",
    "examples"
)

project(":bom").projectDir = file("cotani-bom")
project(":audit").projectDir = file("cotani-audit")
project(":audit-storage").projectDir = file("cotani-audit-storage")
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
project(":npc").projectDir = file("cotani-npc")
project(":region").projectDir = file("cotani-region")
project(":redis").projectDir = file("cotani-redis")
project(":placeholder").projectDir = file("cotani-placeholder")
project(":permission").projectDir = file("cotani-permission")
project(":inventory").projectDir = file("cotani-inventory")
project(":locale").projectDir = file("cotani-locale")
project(":party").projectDir = file("cotani-party")
project(":friend").projectDir = file("cotani-friend")
project(":queue").projectDir = file("cotani-queue")
project(":trade").projectDir = file("cotani-trade")
project(":punishment").projectDir = file("cotani-punishment")
project(":location").projectDir = file("cotani-location")
project(":mail").projectDir = file("cotani-mail")
project(":reward").projectDir = file("cotani-reward")
project(":reward-integration").projectDir = file("cotani-reward-integration")
project(":examples").projectDir = file("docs-examples")


