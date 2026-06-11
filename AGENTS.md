# Repository Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot backend named `acttub-backend`. Main Java source lives in `src/main/java/com/loading/acttub_backend/`, with the application entry point at `ActtubBackendApplication.java`. Runtime configuration belongs in `src/main/resources/`, currently `application.yaml`. Tests mirror the main package under `src/test/java/com/loading/acttub_backend/`.

Keep new code organized by feature or layer beneath the base package. For example, use package names such as `controller`, `service`, `repository`, `domain`, and `config` when those responsibilities appear. Build output is generated under `build/` and should not be edited or committed.

## Build, Test, and Development Commands

Use the Gradle Wrapper so contributors run the same Gradle version:

- `./gradlew bootRun`: start the Spring Boot application locally.
- `./gradlew test`: run the JUnit test suite.
- `./gradlew build`: compile, test, and package the application.
- `./gradlew clean`: remove generated build output.

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Coding Style & Naming Conventions

The project uses Java 21 and Spring Boot 3.5.x. Follow the existing Java style: tabs for indentation in Java files, braces on the same line, and clear class names in `PascalCase`. Use `camelCase` for methods, fields, and local variables. Keep package names lowercase.

Prefer constructor injection for Spring components. Use Lombok only where it improves readability and avoid hiding important behavior behind generated code. Keep YAML configuration keys lowercase and grouped by subsystem.

## Testing Guidelines

Tests use Spring Boot Test with JUnit Platform. Place tests under `src/test/java` using the same package as the code under test. Name test classes with a `Tests` suffix, such as `ActtubBackendApplicationTests`.

Run `./gradlew test` before submitting changes. Add focused tests for new controllers, services, configuration, and error handling. Avoid relying on shared external services in unit tests; prefer mocks or test slices where practical.

## Commit & Pull Request Guidelines

Use short, action-oriented commit messages such as `Add health check endpoint` or `Fix request validation`. Keep each commit focused on one logical change.

Pull requests should include a short summary, test results, and linked issues when available. Include request/response examples for API behavior changes. For branch flow and protection rules, see `docs/branching.md`.

## Security & Configuration Tips

Do not commit secrets, credentials, local database dumps, or generated logs. Keep environment-specific settings outside tracked files when possible, and document required configuration in the PR or README.

## Agent-Specific Instructions

When the user says `ㄱㄱ`, treat it as approval to proceed with the current proposed next step.
