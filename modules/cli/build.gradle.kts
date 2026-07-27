plugins {
    application
}

dependencies {
    implementation(project(":modules:deterministic"))
    implementation(libs.picocli)
}

application {
    mainClass.set("com.dreamthought.saaa.cli.SelfAdaptingAgenticArchitectureCli")
}
