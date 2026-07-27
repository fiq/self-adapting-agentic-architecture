plugins {
    application
}

dependencies {
    implementation(project(":deterministic"))
    implementation(libs.picocli)
}

application {
    mainClass.set("com.dreamthought.saaa.cli.SelfAdaptingAgenticArchitectureCli")
}
