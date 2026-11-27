plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "dev.yukio"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.263-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    runServer {
        minecraftVersion("26.3")
    }
}
