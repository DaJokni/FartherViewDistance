plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.7.1"
    id("io.github.goooler.shadow") version "8.1.7"
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
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
    //commandapi
    implementation("dev.jorel:commandapi-bukkit-shade-mojang-mapped:9.5.1")
    compileOnly("dev.jorel:commandapi-annotations:9.5.1")
    annotationProcessor("dev.jorel:commandapi-annotations:9.5.1")
    //nbtapi
    implementation("de.tr7zw:item-nbt-api:2.12.4")
}

group = "FartherViewDistance"
version = "1.2.0"
description = "FartherViewDistance"
java.sourceCompatibility = JavaVersion.VERSION_21

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks {
    compileJava {
        options.release = 21
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    assemble {
        dependsOn(shadowJar)
    }
    shadowJar {
        archiveFileName.set("${rootProject.name}-${version}.jar")
        dependencies {
            include(dependency("dev.jorel:commandapi-bukkit-shade-mojang-mapped:9.4.1"))
            include(dependency("de.tr7zw:item-nbt-api:2.12.4"))
        }

        relocate("dev.jorel.commandapi", "com.jokni.fartherviewdistance.commandapi")

        relocate("de.tr7zw.changeme.nbtapi", "com.jokni.fartherviewdistance.nbtapi")
    }
}