plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
}

val acceptanceTest by sourceSets.creating {
    java.srcDir("src/acceptanceTest/java")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[acceptanceTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[acceptanceTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("acceptanceTest") {
    description = "Runs outside-in component acceptance tests for application use cases."
    group = "verification"
    testClassesDirs = acceptanceTest.output.classesDirs
    classpath = acceptanceTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
}
