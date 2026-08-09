plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "com.pragma"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.14.0")

    implementation(platform("software.amazon.awssdk:bom:2.31.24"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:sns")

    implementation("com.google.code.gson:gson:2.13.1")

    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("org.hibernate.validator:hibernate-validator:9.0.1.Final")
    implementation("org.glassfish.expressly:expressly:6.0.0-M1")
}

tasks.shadowJar {
    archiveBaseName.set("serverless-java")
    archiveClassifier.set("all")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
