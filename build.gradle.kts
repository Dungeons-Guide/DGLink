val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "1.8.0"
    id("io.ktor.plugin") version "2.2.1"
                id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
    id("com.palantir.docker") version "0.22.1"
}


group = "kr.syeyoung"
version = "0.0.1"
application {
    mainClass.set("kr.syeyoung.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

docker {
    name = "${project.name}:${project.version}"
    files(tasks.shadowJar.get().outputs)
    tag("dgRegistry", "registry.dungeons.guide/github-link:${project.version}")
    setDockerfile(file("Dockerfile"))
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-double-receive:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("dev.kord:kord-core:0.14.0")
    implementation("io.github.crackthecodeabhi:kreds:0.8")
    implementation("io.jsonwebtoken:jjwt-api:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.2")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}