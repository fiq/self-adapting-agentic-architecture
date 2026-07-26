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
    }
}

rootProject.name = "self-adapting-agentic-architecture"

include(
    "core",
    "application",
    "adapters:langchain4j",
    "adapters:git",
    "adapters:sqlite",
    "adapters:checks",
    "benchmarks",
    "cli",
)
