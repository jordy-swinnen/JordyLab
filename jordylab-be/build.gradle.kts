plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.jordy"
version = "0.0.1-SNAPSHOT"
description = "Modular Spring Boot backend powering JordyLab personal projects"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    // Only for com.android.tools.build:apksig (APK signature verification, spec 011 BUG-037) — Google
    // publishes it there, not on Maven Central. Restricted to that group so nothing else resolves from it.
    google {
        content {
            includeGroup("com.android.tools.build")
        }
    }
}

extra["springAiVersion"] = "2.0.1"
extra["springModulithVersion"] = "2.1.1"

dependencies {
    implementation("com.android.tools.build:apksig:8.13.2")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.rometools:rome:2.1.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.springframework.ai:spring-ai-vector-store-advisor")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.wiremock:wiremock-standalone:3.13.0")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.ai:spring-ai-spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:3.17.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Tests that boot a Spring context (JordylabApplicationTests, KeycloakIntegrationTest,
    // @ApplicationModuleTest) need a valid profile active or EnvironmentProfileGuard fails them
    // fast, per FR-002. "local" matches how the app actually runs outside prod.
    systemProperty("spring.profiles.active", "local")
    // Test-only values, set here rather than in src/test/resources/application.yaml: a file with that name
    // replaces the main application.yaml on the test classpath, so tests never saw the real shared config
    // (spec 011 BUG-035).
    systemProperty("spring.ai.anthropic.api-key", "test-key-not-used")
    systemProperty("spring.ai.anthropic.chat.options.model", "claude-sonnet-5")
}

// Real-model tests are never part of `test`: they cost money and need keys (spec 013 FR-018, quickstart A3).
tasks.test {
    useJUnitPlatform {
        excludeTags("golden-live", "integration-igdb")
    }
}

// `./gradlew goldenLive` asks the real models the golden questions and prints pass rates and token cost. It needs
// OPENROUTER_API_KEY (and ANTHROPIC_API_KEY for the fallback) in the environment; the values are never printed.
val goldenLive by tasks.registering(Test::class) {
    description = "Runs the LibBot golden questions against the real models (costs tokens)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("golden-live")
    }
    System.getenv("ANTHROPIC_API_KEY")?.let { systemProperty("spring.ai.anthropic.api-key", it) }
    System.getenv("OPENROUTER_API_KEY")?.let { environment("OPENROUTER_API_KEY", it) }
    testLogging {
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
}

// `./gradlew igdbCheck` re-reads IGDB's platform list and fails when a platform in `PlatformCatalog` no longer carries its
// stored IGDB name. Needs IGDB_CLIENT_ID and IGDB_CLIENT_SECRET in the environment.
val igdbCheck by tasks.registering(Test::class) {
    description = "Checks the platform catalog's IGDB ids against live IGDB."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration-igdb")
    }
    listOf("IGDB_CLIENT_ID", "IGDB_CLIENT_SECRET").forEach { key -> System.getenv(key)?.let { environment(key, it) } }
    testLogging {
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
}

// The Spring Boot bootstrap class is a single `main` method delegating to
// SpringApplication.run — excluded from coverage like any framework entrypoint.
val coverageExcludes = listOf("**/JordylabApplication.class")

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } })
    violationRules {
        rule {
            element = "PACKAGE"
            limit {
                minimum = 0.80.toBigDecimal()
            }
        }
    }
}

tasks.jacocoTestReport {
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } })
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
