plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.hypnosia.licenseserver.LocalLicenseServerKt")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
