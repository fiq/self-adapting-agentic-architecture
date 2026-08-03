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
    implementation(platform(libs.mcp.bom))
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.mcp)
    implementation(libs.jgit)
    implementation(libs.neo4j.driver)
    implementation(libs.smallrye.config)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flyway.core)
    constraints {
        implementation(libs.jackson3.databind) {
            because("3.1.5 fixes GHSA-5gvw-p9qm-jgwh in the Flyway/MCP transitive graph")
        }
    }
    testImplementation(libs.wiremock)
}
