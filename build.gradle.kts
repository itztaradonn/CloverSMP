import java.security.MessageDigest

plugins {
    java
}

group = "net.cloversmp"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
    }

    // ./gradlew resourcePack → build/distributions/CloverBlessing-pack.zip plus the SHA-1 for server.properties
    register<Zip>("resourcePack") {
        from("resourcepack")
        archiveFileName.set("CloverBlessing-pack.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        isPreserveFileTimestamps = false // same art → same zip → same SHA-1
        isReproducibleFileOrder = true
        doLast {
            val zip = archiveFile.get().asFile
            val sha1 = MessageDigest.getInstance("SHA-1").digest(zip.readBytes())
                .joinToString("") { "%02x".format(it) }
            println("Resource pack: $zip")
            println("resource-pack-sha1=$sha1")
        }
    }
}
