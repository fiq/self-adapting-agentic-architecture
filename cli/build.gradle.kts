plugins {
    application
}

dependencies {
    implementation(project(":application"))
    implementation(libs.picocli)
}

application {
    mainClass.set("io.github.selfadaptingagenticarchitecture.cli.SelfAdaptingAgenticArchitectureCli")
}
