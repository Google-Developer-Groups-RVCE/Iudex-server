plugins {
    java
    application
}

group = "com.iudex"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // javalin for web stuff
    implementation("io.javalin:javalin:7.2.3")

    // db and connection pool
    implementation("com.h2database:h2:2.2.224")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // SQL mapping, jdbi
    implementation(platform("org.jdbi:jdbi3-bom:3.54.0"))
    implementation("org.jdbi:jdbi3-core")
    implementation("org.jdbi:jdbi3-sqlobject")

    // db migrations
    implementation("org.flywaydb:flyway-core:10.17.1")

    // logging: the api we compile against, plus something to print it
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    // Argon2id and jwt
    implementation("com.password4j:password4j:1.8.2")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // for testing
    testImplementation("io.javalin:javalin-testtools:7.2.3")

    // so tests can assert on parsed JSON rather than exact formatting.
    // it arrives through javalin anyway, declared here because we use
    // it directly and should not rely on someone else's transitive.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
}

// i kinda figured out testing
testing {
    suites {
        val test = getByName<JvmTestSuite>("test") {
            useJUnitJupiter("6.0.1")
        }

        val integrationTest = register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter("6.0.1")

            dependencies {
                implementation(project())
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

application {
    mainClass.set("gdg.iudex.Main")
}
