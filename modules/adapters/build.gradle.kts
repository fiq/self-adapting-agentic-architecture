plugins {
    `java-library`
}

// One adapter module. The boundary that matters is deterministic <- adapters, not adapter vs
// adapter, so four two-file modules were not earning their build files.
//
// The cost is that these provider libraries now share one compile classpath, so Gradle no longer
// stops the git adapter importing LangChain4j. check-architecture-boundaries confines each
// dependency to its own package instead; that rule is what keeps this merge honest.
dependencies {
    implementation(project(":deterministic"))
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flyway.core)
}
