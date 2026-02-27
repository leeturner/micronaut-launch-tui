# Micronaut CLI Template

A Kotlin-based Micronaut CLI application template with enhanced tooling and testing libraries.

## Features

This template extends the base Micronaut CLI application with the following enhancements:

### Build & Dependency Management
- **Gradle Version Catalogue**: Centralized dependency management using `libs.versions.toml`
- **Java 21**: Modern Java runtime with toolchain support

### Code Quality & Linting
- **Kotlinter**: Kotlin code formatter and linter following ktlint rules
- **Detekt**: Static code analysis for Kotlin with custom configuration

### Libraries
- **Arrow Core**: Functional programming library for Kotlin, providing functional data types and utilities
- **Strikt**: Modern assertion library for Kotlin tests with fluent API
  - Includes Arrow integration for functional type assertions

### CI/CD & Automation
- **GitHub Actions**: Automated build workflow running on push and PR to main branch
- **Dependabot**: Daily automated dependency updates for Gradle dependencies
- **Dependency Submission**: Automated dependency graph generation for security alerts

### CLI Framework
- **Picocli**: Command-line interface framework integrated with Micronaut
- Configured to return proper exit codes for command execution

## How to use this template

Click on the "Use this template" button on the GitHub page to create a new project based on this template.

Once you have created your project, you can update the following parts of the project
to start developing your application:

* The project name in `settings.gradle.kts`
* The project name in `application.properties`
* Rename the package in the `src/main` and `src/test` directories
* Update to the same package name in `build.gradle.kts` (`group`, `mainClass`, `micronaut/annotations`)
* Delete or rename the source code as required

## Micronaut Documentation

- [User Guide](https://docs.micronaut.io/4.10.2/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.10.2/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.10.2/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
---

- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
- [Shadow Gradle Plugin](https://gradleup.com/shadow/)

## Feature kapt documentation

- [Micronaut Kotlin Annotation Processing (KAPT) documentation](https://docs.micronaut.io/snapshot/guide/#kapt)

- [https://kotlinlang.org/docs/kapt.html](https://kotlinlang.org/docs/kapt.html)


