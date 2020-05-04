plugins {
    java
    kotlin("jvm") version "1.3.61"
    id("org.springframework.boot") version "2.1.9.RELEASE"
    id("io.spring.dependency-management") version "1.0.8.RELEASE"
    //id("org.jetbrains.kotlin.plugin.allopen") version "1.3.61"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.61"
}

group = "ru.exrates"
version = "0.1.1"

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
    implementation("org.projectreactor:reactor-spring:1.0.1.RELEASE")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
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
    compileKotlin {
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
        val startFolder = file("C:\\Users\\Пендальф Синий\\Desktop\\exrates\\")
        from(file("$buildD/$archieveName"))
        into(startFolder)

    }

}