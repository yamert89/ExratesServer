import org.jetbrains.kotlin.konan.properties.saveToFile
import java.util.Properties
plugins {
    java
    kotlin("jvm") version "1.3.72"
    id("org.springframework.boot") version "2.1.9.RELEASE"
    id("io.spring.dependency-management") version "1.0.8.RELEASE"
    //id("org.jetbrains.kotlin.plugin.allopen") version "1.3.61"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.72"
}

group = "ru.exrates"
version = "0.4.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.projectreactor:reactor-spring:1.0.1.RELEASE")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    runtimeOnly("mysql:mysql-connector-java")
    runtimeOnly("org.postgresql:postgresql:42.2.8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.postgresql:postgresql:42.2.8")
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_10
}

tasks {
    register<Task>("updateVersion"){
        val props = Properties()
        val file = file("$projectDir/src/main/resources/application.properties")
        val inS = file.inputStream()
        props.load(inS)
        val file2 = org.jetbrains.kotlin.konan.file.File("$projectDir/src/main/resources/application.properties")

        props["app.version"] = version
        props.saveToFile(file2)
    }
    compileKotlin {
        dependsOn("updateVersion")
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    bootJar{
        archiveFileName.set("exratesServer.jar")
        launchScript()
    }

    bootJar.get().dependsOn.add("classes")

    register<Copy>("copy") {
        dependsOn(bootJar)
        val archieveName = "exratesServer.jar"
        val buildD = "$projectDir/build/libs/"
        var startFolder = file("C:\\Users\\Пендальф Синий\\Desktop\\exrates\\")
        if(!startFolder.exists()) startFolder = file("D:/my/")
        delete(file(startFolder.path + archieveName))
        from(file("$buildD/$archieveName"))
        into(startFolder)
    }

}