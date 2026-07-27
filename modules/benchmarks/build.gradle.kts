plugins {
    `java-library`
}

dependencies {
    implementation(project(":modules:deterministic"))
    implementation(project(":modules:domain"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}
