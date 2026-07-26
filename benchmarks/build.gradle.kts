plugins {
    `java-library`
}

dependencies {
    implementation(project(":application"))
    implementation(project(":core"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}
