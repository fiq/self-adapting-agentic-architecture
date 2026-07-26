plugins {
    `java-library`
}

dependencies {
    implementation(project(":application"))
    implementation(libs.langchain4j)
}
