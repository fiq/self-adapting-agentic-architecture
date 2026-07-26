plugins {
    `java-library`
}

dependencies {
    implementation(project(":application"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.flyway.core)
}
