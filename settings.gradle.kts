rootProject.name = "cotani"

include(
    "cache",
    "config",
    "core",
    "economy",
    "text",
    "item",
    "task",
    "teleport",
    "storage",
    "user"
)

project(":cache").projectDir = file("cotani-cache")
project(":config").projectDir = file("cotani-config")
project(":core").projectDir = file("cotani-core")
project(":economy").projectDir = file("cotani-economy")
project(":text").projectDir = file("cotani-text")
project(":item").projectDir = file("cotani-item")
project(":task").projectDir = file("cotani-task")
project(":teleport").projectDir = file("cotani-teleport")
project(":storage").projectDir = file("cotani-storage")
project(":user").projectDir = file("cotani-user")
