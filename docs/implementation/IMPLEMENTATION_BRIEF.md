# Implementation Brief

## Repository Layout

Use these top-level locations:

- `features/` - canonical Gherkin specifications and AI generation inputs.
- `app/` - generated application code.
- `docs/implementation/` - implementation reports, metrics, assumptions, and
  delivery notes.

Do not modify files under `features/` unless a project maintainer explicitly
requests a feature-specification change.

Do not place generated code, build output, logs, temporary files, or implementation
reports under `features/`.

## Open Decisions

- Target language: Java.
- Target framework/runtime: Spring Boot.
- Java version: 21.
- Build tool: Maven.
- Implementation scope: phased.
- First implementation slice: project skeleton plus `features/booking-create.feature`.
- Later slices should expand feature coverage incrementally and preserve the
  existing generated app structure unless a change is explicitly justified.
- Validation commands:
  - `./mvnw test`
  - `./mvnw verify`
- If the Maven wrapper does not exist yet, the first implementation slice should
  create it. Until then, use `mvn test` and `mvn verify`.
