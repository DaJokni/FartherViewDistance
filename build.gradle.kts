plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.7.7"
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
}

dependencies {
    //paper
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    //commandapi
    implementation("dev.jorel:commandapi-bukkit-shade-mojang-mapped:9.7.0")
    compileOnly("dev.jorel:commandapi-annotations:9.7.0")
    annotationProcessor("dev.jorel:commandapi-annotations:9.7.0")
}

group = "com.jokni"
version = "1.3.1"
description = "FartherViewDistance"
java.sourceCompatibility = JavaVersion.VERSION_21

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}


tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    processResources {
        outputs.upToDateWhen { false }
        expand(project.properties)
    }
    assemble {
        dependsOn(shadowJar)
    }
    shadowJar {
        archiveFileName.set("${rootProject.name}-${version}.jar")
        relocate("dev.jorel.commandapi", "com.jokni.fartherviewdistance.commandapi")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}