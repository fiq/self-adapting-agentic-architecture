plugins {
    application
}

dependencies {
    implementation(project(":deterministic"))
    implementation(project(":adapters"))
    implementation(project(":benchmarks"))
    implementation(libs.picocli)
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.wiremock)
}

application {
    applicationName = "saaa"
    mainClass.set("com.dreamthought.saaa.cli.MutationLoopCli")
}

val acceptanceTest by sourceSets.creating {
    java.srcDir("src/acceptanceTest/java")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[acceptanceTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[acceptanceTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("acceptanceTest") {
    description = "Runs outside-in acceptance tests for CLI commands."
    group = "verification"
    testClassesDirs = acceptanceTest.output.classesDirs
    classpath = acceptanceTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
}
