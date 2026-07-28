plugins {
    application
}

dependencies {
    implementation(project(":deterministic"))
    implementation(project(":adapters"))
    implementation(libs.picocli)
}

application {
    mainClass.set("com.dreamthought.saaa.cli.MutationLoopCli")
}
