plugins {
    java
    `java-library`
    `maven-publish`
}

val springBootVersion = "3.5.11"
val springCloudVersion = "2025.0.1"

allprojects {
    group = "io.vaultglue"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        val bom = platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        val cloudBom = platform("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        implementation(bom)
        implementation(cloudBom)
        annotationProcessor(bom)
        annotationProcessor(cloudBom)
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
    }
}
