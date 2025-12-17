plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring)
    alias(libs.plugins.spring.management)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "top.tbpdt"
version = "3.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter)
    implementation(libs.simbot.spring)
    implementation(libs.simbot.component.qq)
    implementation(libs.ktor.client.java)
    implementation(libs.spring.web)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
}
