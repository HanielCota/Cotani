rootProject.name = "cotani"

include(
    "config",
    "core",
    "text",
    "item",
    "task",
    "teleport",
    "storage"
)

project(":config").projectDir = file("cotani-config")
project(":core").projectDir = file("cotani-core")
project(":text").projectDir = file("cotani-text")
project(":item").projectDir = file("cotani-item")
project(":task").projectDir = file("cotani-task")
project(":teleport").projectDir = file("cotani-teleport")
project(":storage").projectDir = file("cotani-storage")
