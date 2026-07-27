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

// Layers are ordered inward-to-outward. Each one may depend only on the layers above it.
//
//   domain         the vocabulary being evolved; no dependencies at all
//   deterministic  validation, scoring, promotion and ports; nothing provider-aware or random
//   adapters       the model, Git, SQLite and command execution
//   cli            entry point
//   benchmarks     JMH evidence used by scoring
// Each layer is included by its own name and mapped to its directory, so `subprojects {}` means
// exactly these five and no empty `:modules` container project is created.
listOf("domain", "deterministic", "adapters", "benchmarks", "cli").forEach { layer ->
    include(layer)
    project(":$layer").projectDir = file("modules/$layer")
}
