plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}
repositories {
    mavenCentral()
    maven("https://releases.aspose.com/java/repo/") { content { includeGroup("com.aspose") } }
}
dependencies {
    implementation("com.github.auties00:cobalt:0.0.10") {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-netty:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-server-status-pages:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.flywaydb:flyway-database-postgresql:11.10.2")
    implementation("software.amazon.awssdk:s3:2.32.10")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test"))
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
}
kotlin { jvmToolchain(21) }
application { mainClass.set("gateway.ApplicationKt") }
tasks.test { useJUnitPlatform() }
detekt { config.setFrom(files("detekt.yml")); buildUponDefaultConfig = false }
