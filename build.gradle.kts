plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems that only have JDK 11 installed for example.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
}

dependencies {
    //paper
    paperweight.paperDevBundle("1.20.5-R0.1-SNAPSHOT")
    //commandapi
    implementation("dev.jorel:commandapi-bukkit-shade:9.4.0-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-annotations:9.4.0-SNAPSHOT")
    annotationProcessor("dev.jorel:commandapi-annotations:9.4.0-SNAPSHOT")
    //nbtapi
    implementation("de.tr7zw:item-nbt-api:2.12.4-SNAPSHOT")
}

group = "FartherViewDistance"
version = "1.2.0"
description = "FartherViewDistance"
java.sourceCompatibility = JavaVersion.VERSION_17

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks {
    compileJava {
        options.release = 21
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    shadowJar {
        dependencies {
            include(dependency("dev.jorel:commandapi-bukkit-shade:9.4.0-SNAPSHOT"))
            include(dependency("de.tr7zw:item-nbt-api:2.12.4-SNAPSHOT"))
        }

        relocate("dev.jorel.commandapi", "com.jokni.fartherviewdistance.commandapi")

        relocate("de.tr7zw.changeme.nbtapi", "com.jokni.fartherviewdistance.nbtapi")
    }
}