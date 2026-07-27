plugins {
    application
}

dependencies {
    implementation(project(":application"))
    implementation(libs.picocli)
}

application {
    mainClass.set("com.dreamthought.saaa.cli.SelfAdaptingAgenticArchitectureCli")
}
