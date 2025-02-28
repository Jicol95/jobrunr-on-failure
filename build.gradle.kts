plugins {
    kotlin("jvm") version "2.0.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.jobrunr.io/private-releases/")
        credentials {
            username = "pleo.io"
            password = System.getenv("JOB_RUNR_REPO_PASSWORD")
        }
        content {
            includeGroup("org.jobrunr")
        }
    }
    mavenCentral()
}

val exposedVersion = "0.53.0"
val hikariVersion = "5.1.0"
val postgresVersion = "42.7.4"

dependencies {
    testImplementation(kotlin("test"))
    implementation(platform("org.jetbrains.exposed:exposed-bom:$exposedVersion"))
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-java-time")
    implementation("org.jetbrains.exposed:exposed-jdbc")
    implementation("org.jetbrains.exposed:exposed-json")
    implementation("org.jetbrains.exposed:exposed-money")
    implementation("org.jobrunr:jobrunr-pro:7.4.1")
    implementation("org.jobrunr:jobrunr-pro-kotlin-1.9-support:7.4.1")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
