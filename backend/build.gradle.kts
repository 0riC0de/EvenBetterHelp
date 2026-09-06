plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}
repositories { mavenCentral() }
val ktorVersion = "3.2.3"
dependencies {
    listOf("core", "netty", "content-negotiation", "auth", "auth-jwt", "websockets", "status-pages", "call-logging", "cors").forEach {
        implementation("io.ktor:ktor-server-$it:$ktorVersion")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.insert-koin:koin-ktor:4.1.0")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.flywaydb:flyway-database-postgresql:11.10.2")
    implementation("software.amazon.awssdk:s3:2.32.10")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
}
kotlin { jvmToolchain(21) }
application { mainClass.set("helpdesk.ApplicationKt") }
tasks.test { useJUnitPlatform() }
detekt { config.setFrom(files("detekt.yml")); buildUponDefaultConfig = false }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xjsr305=strict")
}
tasks.register<JavaExec>("runPostgres") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("helpdesk.RunPostgresKt")
}

