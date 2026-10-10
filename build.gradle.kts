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
    implementation(libs.micronaut.http.client)

    implementation(platform(libs.tamboui.bom))
    // For Toolkit DSL (recommended)
    implementation(libs.tamboui.toolkit)
    // JLine backend (required)
    implementation(libs.tamboui.jline3.backend)

    implementation(libs.kotlin.reflect)
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
    sourceCompatibility = JavaVersion.toVersion("25")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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
  openapi {
    client(file("src/main/openapi/micronaut-launch-4.10.9.yml")) {
      apiPackageName.set("com.leeturner.mtui.adapters.outbound.http.client.api")
      apiNamePrefix.set("MicronautLaunch")

      modelPackageName.set("com.leeturner.mtui.adapters.outbound.http.client.model")
      modelNamePrefix.set("MicronautLaunch")

      clientId.set("micronaut-launch")

      // Supports Kotlin codegen too
      lang.set("kotlin")
      useSealed.set(true)
      useReactive.set(false)
      useOptional.set(true)

      // The generator inlines `items: allOf: [$ref]` as duplicate *AllOfOptions models, point them back at the real ones
      val modelPackage = modelPackageName.get()
      schemaMapping.set(
        listOf("ApplicationType", "JdkVersion", "Language", "TestFramework", "BuildTool").associate {
          "${it}SelectOptions_allOf_options" to "$modelPackage.MicronautLaunch${it}Info"
        },
      )
    }
  }
}

tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("dockerfile") {
  baseImage = "eclipse-temurin:25-jre"
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "25"
}

tasks.withType<LintTask> {
  exclude { it.file.path.contains("build/generated/openapi") }
}

tasks.withType<FormatTask> {
  exclude { it.file.path.contains("build/generated/openapi") }
}
