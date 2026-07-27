plugins {
    `java-library`
}

dependencies {
    implementation(project(":deterministic"))
    implementation(project(":domain"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}
