plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("kapt") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = "co.statu.parsek"
version =
    (if (project.hasProperty("version") && project.findProperty("version") != "unspecified") project.findProperty("version") else "local-build")!!

val pf4jVersion: String by project
val vertxVersion: String by project
val gsonVersion: String by project
val handlebarsVersion: String by project
val bootstrap = (project.findProperty("bootstrap") as String?)?.toBoolean() ?: false
val pluginsDir: File? by rootProject.extra

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    if (bootstrap) {
        compileOnly(project(mapOf("path" to ":Parsek")))
    } else {
        compileOnly("com.github.StatuParsek:Parsek:main-SNAPSHOT")
    }

    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))

    compileOnly("org.pf4j:pf4j:${pf4jVersion}")
    kapt("org.pf4j:pf4j:${pf4jVersion}")

    compileOnly("io.vertx:vertx-lang-kotlin:$vertxVersion")
    compileOnly("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")

    implementation("io.vertx:vertx-jdbc-client:$vertxVersion")

    // https://mvnrepository.com/artifact/com.clickhouse/clickhouse-jdbc
    implementation("com.clickhouse:clickhouse-jdbc:0.6.0")

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    compileOnly("com.google.code.gson:gson:$gsonVersion")

    // https://mvnrepository.com/artifact/org.lz4/lz4-java
    implementation("org.lz4:lz4-java:1.8.0")

    // https://mvnrepository.com/artifact/org.apache.httpcomponents.client5/httpclient5
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3")
}

tasks.named("jar").configure {
    enabled = false
}

tasks {
    shadowJar {
        val pluginId: String by project
        val pluginClass: String by project
        val pluginProvider: String by project
        val pluginDependencies: String by project

        manifest {
            attributes["Plugin-Class"] = pluginClass
            attributes["Plugin-Id"] = pluginId
            attributes["Plugin-Version"] = version
            attributes["Plugin-Provider"] = pluginProvider
            attributes["Plugin-Dependencies"] = pluginDependencies
        }

        archiveFileName.set("$pluginId-$version.jar")

        dependencies {
            exclude(dependency("io.vertx:vertx-core"))
            exclude {
                it.moduleGroup == "io.netty" || it.moduleGroup == "org.slf4j"
            }
        }
    }

    register("copyJar") {
        pluginsDir?.let {
            copy {
                from(shadowJar.get().archiveFile.get().asFile.absolutePath)
                into(it)
            }
        }
    }

    build {
        dependsOn(shadowJar)
        dependsOn("copyJar")
    }
}

publishing {
    repositories {
        maven {
            name = "parsek-plugin-database"
            url = uri("https://maven.pkg.github.com/StatuParsek/parsek-plugin-database")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME_GITHUB")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("TOKEN_GITHUB")
            }
        }
    }

    publications {
        create<MavenPublication>("shadow") {
            project.extensions.configure<com.github.jengelman.gradle.plugins.shadow.ShadowExtension> {
                artifactId = "parsek-plugin-database"
                component(this@create)
            }
        }
    }
}