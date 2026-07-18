---
name: runtime-java
description: Specialise Java runtime, build, framework and test conventions.
---

# Runtime: Java

Detect Maven, Gradle, JDK version, Spring Boot, Spring Web, WebFlux, Quarkus,
Micronaut, JHipster patterns, Kafka roles, LangChain4j, Spring AI, JDBC, JPA,
jOOQ, Flyway, Liquibase, Testcontainers and ArchUnit.

Classify application shape from code and config, not framework. Prefer the
existing build tool. Add migration and test harness guidance only when evidence
requires it.

## Language smells (for review-loop)

Anemic domain models; field injection over constructor injection; swallowed or
overly broad checked exceptions; leaking JPA entities across boundaries;
`Optional` fields or parameters; static mutable state; primitive obsession; god
services; nullable returns without contract; overuse of reflection.
