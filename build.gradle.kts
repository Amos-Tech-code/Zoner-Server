plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.21"
}

group = "com.amos_tech_code"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")

}

repositories {
    mavenCentral()
}

dependencies {

    implementation("ch.qos.logback:logback-classic:1.5.13")
    // For datetime support
    implementation("org.jetbrains.exposed:exposed-java-time:0.39.1")
    // Ktor Core
    implementation("io.ktor:ktor-server-core-jvm:2.3.0")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.0")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.0")
    implementation("io.ktor:ktor-server-auth:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Ktor Features
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.0")
    implementation("io.ktor:ktor-server-config-yaml-jvm:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.postgresql:postgresql:42.7.7")
    // Hikari CP
    implementation("com.zaxxer:HikariCP:5.1.0")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")
    // BCrypt
    implementation("org.mindrot:jbcrypt:0.4")
    // Environment Variables
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.13")

    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")

    //  WebSockets
    implementation("io.ktor:ktor-server-websockets:1.6.0")

    // Ktor client
    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")

    // Add these Jackson dependencies
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    
    // Fix Firebase dependency issue
    implementation("com.google.guava:guava:32.1.2-jre")

    //email client
    implementation("org.simplejavamail:simple-java-mail:8.6.3")

    //koin
    implementation("io.insert-koin:koin-core-coroutines:3.4.1")
    implementation("io.insert-koin:koin-ktor:3.4.1")
    implementation("io.insert-koin:koin-logger-slf4j:3.4.1")

    // Low-level video/audio processing
    implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9")
    implementation("org.bytedeco:javacv-platform:1.5.9")
    // File type detection + metadata extraction
    implementation("org.apache.tika:tika-core:2.9.0")
    // BlurHash

}
