import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kapt)
  alias(libs.plugins.kotlin.allopen)
  alias(libs.plugins.micronaut.application)
  alias(libs.plugins.micronaut.openapi)
  alias(libs.plugins.shadow)
  alias(libs.plugins.kotlinter)
  alias(libs.plugins.detekt)
}

version = "0.1"
group = "com.leeturner.mtui"

repositories {
    mavenCentral()
}

dependencies {
    kapt(libs.picocli.codegen)
    kapt(libs.micronaut.serde.processor)

    implementation(libs.picocli)
    implementation(libs.micronaut.kotlin.extension.functions)
    implementation(libs.micronaut.kotlin.runtime)
    implementation(libs.micronaut.picocli)
    implementation(libs.micronaut.serde.jackson)
    implementation("io.micronaut:micronaut-http-client")
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.arrow.core)

    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.strikt.core)
    testImplementation(libs.strikt.arrow)
    testImplementation(libs.wiremock.micronaut)
}


application {
    mainClass = "com.leeturner.mtui.MtuiCommand"
}
java {
    sourceCompatibility = JavaVersion.toVersion("21")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

detekt {
  toolVersion = libs.versions.detekt.get()
  config.setFrom(file("config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
}

micronaut {
  version(libs.versions.micronaut.version.get())
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.leeturner.mtui.*")
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}

tasks.withType<LintTask> {
  exclude { it.file.path.contains("build/generated/openapi") }
}

tasks.withType<FormatTask> {
  exclude { it.file.path.contains("build/generated/openapi") }
}

micronaut {
  openapi {
    client(file("src/main/openapi/micronaut-launch-4.10.9.yml")) {
      apiPackageName.set("com.leeturner.mtui.domain.launch.infrastructure.api")
      apiNamePrefix.set("MicronautLaunch")

      modelPackageName.set("com.leeturner.mtui.domain.launch.infrastructure.model")
      modelNamePrefix.set("MicronautLaunch")

      clientId.set("micronaut-launch")

      // Supports Kotlin codegen too
      lang.set("kotlin")
      useSealed.set(true)
      useReactive.set(false)
      useOptional.set(true)
    }
  }
}
