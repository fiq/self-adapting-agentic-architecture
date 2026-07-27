plugins {
    `java-library`
}

// One adapter module. The boundary that matters is deterministic <- adapters, not adapter vs
// adapter, so four two-file modules were not earning their build files.
dependencies {
    implementation(project(":modules:deterministic"))
    implementation(libs.langchain4j)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flyway.core)
}
