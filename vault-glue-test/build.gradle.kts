dependencies {
    api(project(":vault-glue-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-test")
    api("org.testcontainers:vault:1.20.4")
    api("org.testcontainers:junit-jupiter:1.20.4")
    api("org.testcontainers:mysql:1.20.4")
}
