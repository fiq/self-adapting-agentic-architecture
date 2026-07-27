import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

allprojects {
    group = "com.dreamthought.saaa"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    val sourceSets = extensions.getByType<SourceSetContainer>()
    val integrationTest = sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.named("main").get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

    configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
    configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

    tasks.register<Test>("integrationTest") {
        description = "Runs real-dependency integration tests for this module."
        group = "verification"
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        shouldRunAfter(tasks.named("test"))
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
        "testImplementation"("net.jqwik:jqwik:1.9.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
