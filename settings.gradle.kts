rootProject.name = "cotani"

include(
    "core",
    "text",
    "item",
    "task",
    "teleport"
)

project(":core").projectDir = file("cotani-core")
project(":text").projectDir = file("cotani-text")
project(":item").projectDir = file("cotani-item")
project(":task").projectDir = file("cotani-task")
project(":teleport").projectDir = file("cotani-teleport")
