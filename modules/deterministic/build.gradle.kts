plugins {
    `java-library`
    // The source-structure conformance suite ships as test fixtures, not as tests. Every real
    // frontend lives in adapters, and a suite in src/test could not be compiled against from
    // there — which would leave "supported exactly when it passes the suite" unenforceable.
    `java-test-fixtures`
}

dependencies {
    api(project(":domain"))
    // The conformance suite asserts, so AssertJ is part of what a consuming frontend module gets:
    // `api`, not `implementation`, because the frontend's own test compiles against these calls.
    testFixturesApi(project(":domain"))
    testFixturesApi("org.assertj:assertj-core:3.27.7")
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
