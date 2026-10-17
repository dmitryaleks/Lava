plugins {
    `java-library`
    jacoco
}

group = "com.simplelibs"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
    // Test-only oracle for differential and API-parity tests. Main code must never depend on Guava.
    testImplementation("com.google.guava:guava:27.0.1-jre")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.compileTestJava {
    // Tests exercise deprecated mutators and raw types on purpose; keep the remaining lint visible.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-deprecation", "-Xlint:-serial", "-Xlint:-rawtypes", "-Xlint:-unchecked", "-Xlint:-removal", "-Xlint:-varargs"))
}

tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("quiet", true)
    }
}

tasks.test {
    useJUnitPlatform {
        if (project.hasProperty("skipGcTests")) {
            excludeTags("gc")
        }
    }
    // MapsTest reflects into HashMap.table to verify capacity computations.
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

val verifyNoGuavaInMain by tasks.registering {
    group = "verification"
    description = "Fails if main sources reference Guava or the Checker Framework in any form."
    val mainSources = fileTree("src/main") { include("**/*.java") }
    val root = projectDir
    inputs.files(mainSources)
    doLast {
        val forbidden = Regex("""com\.google\.|org\.checkerframework\.""")
        val offenders = mainSources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException("Guava/Checker Framework references found in src/main:\n" + offenders.joinToString("\n"))
        }
    }
}

tasks.check {
    dependsOn(verifyNoGuavaInMain, tasks.jacocoTestCoverageVerification)
}
