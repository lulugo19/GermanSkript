import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "1.3.71"
    application
}

group = "germanskript"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClassName = "StartKt"
}

// https://stackoverflow.com/questions/45747112/kotlin-and-gradle-reading-from-stdio
// Das erlaubt uns wenn wir 'run' ausführen von der Standardeingabe zu lesen
val run by tasks.getting(JavaExec::class) {
    standardInput = System.`in`
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")

    // test dependencies
    testImplementation("org.assertj:assertj-core:3.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    jar {
        manifest {
            attributes(
                "Main-Class" to "StartKt"
            )
        }
        // create a fat jar
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }

    test {
        useJUnitPlatform()
        testLogging {
            events = setOf(
                TestLogEvent.STARTED,
                TestLogEvent.PASSED,
                TestLogEvent.FAILED
            )

            // show standard out and standard error of the test
            // JVM(s) on the console
            showStandardStreams = true
        }
    }
}
/*
tasks.withType(JavaCompile::class.java) {
    options.encoding = "UTF-8"
}
*/