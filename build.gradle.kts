plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.5.11"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
    //commandapi
    implementation("dev.jorel:commandapi-bukkit-shade:9.3.0-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-annotations:9.3.0-SNAPSHOT")
    annotationProcessor("dev.jorel:commandapi-annotations:9.3.0-SNAPSHOT")
    //nbtapi
    implementation("de.tr7zw:item-nbt-api:2.12.1")
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


tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    build {
        dependsOn(reobfJar)
    }
    assemble {
        dependsOn(reobfJar)
    }
    shadowJar {
        dependencies {
            include(dependency("dev.jorel:commandapi-bukkit-shade:9.3.0-SNAPSHOT"))
            include(dependency("de.tr7zw:item-nbt-api:2.12.1"))
        }

        relocate("dev.jorel.commandapi", "com.jokni.fartherviewdistance.commandapi")

        relocate("de.tr7zw.changeme.nbtapi", "com.jokni.fartherviewdistance.nbtapi")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}