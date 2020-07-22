plugins {
    kotlin("jvm") version "1.3.71"
    application
}

group = "germanscript"
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
}

/*
tasks.withType(JavaCompile::class.java) {
    options.encoding = "UTF-8"
}
*/