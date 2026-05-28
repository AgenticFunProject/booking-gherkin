# booking-gherkin Agent Instructions

## Project Identity

This repository is `booking-gherkin`.

- Primary content: top-level `features/`

This repository is intended to hold Gherkin specifications that can guide project
generation and validation. It should remain usable by anyone cloning it with a
standard Git workflow.

## Generation Purpose

The files in `features/` are intended to be used as AI generation inputs for
building the target project.

- Treat feature files as product specifications, not only test artifacts.
- Treat feature files as canonical project roots. Do not change them unless the
  user or project maintainer explicitly asks for a feature-specification change.
- Write scenarios so an AI system can infer behavior, boundaries, data rules,
  and expected outcomes without relying on hidden project context.
- Prefer explicit acceptance criteria and concrete examples over shorthand.
- Keep wording stable and intentional; avoid casual phrasing that could be
  misinterpreted by a generator.
- When changing a feature, consider the downstream generated implementation that
  may be produced from it.

## Scope Rules

- Work only inside this repository unless the user explicitly asks for changes
  elsewhere.
- Treat external repositories as references, not as files to edit from here.
- Do not modify files under `features/` as a drive-by cleanup, refactor, style
  pass, or inferred correction.
- Do not silently copy external feature files into this repository.
- If syncing from another source, record what changed and why.
- Preserve intentional divergence when this repository is the canonical Gherkin
  source.

## Gherkin Style

- Keep feature files business-readable and implementation-agnostic.
- Prefer domain language over Java class names, database tables, or internal APIs.
- Use `Feature`, `Background`, `Scenario`, and `Scenario Outline` consistently.
- Keep scenarios independent, deterministic, and safe to run in any order.
- Prefer concrete examples with clear inputs, actions, and expected outputs.
- Avoid overfitting scenarios to current implementation quirks unless the quirk is
  intentional product behavior.
- Avoid ambiguity in actors, permissions, time assumptions, identifiers, and
  external system behavior.

## Repository Layout

- `features/` is the main product of this repository.
- Keep supporting documentation close to the feature files it explains.
- Add new feature files under `features/` unless a broader layout is deliberately
  introduced.

## Workflow

- Use the repository's normal issue and pull request workflow.
- Create focused branches for changes; do not work directly on the default branch for
  non-trivial updates.
- Keep commits focused on one purpose.
- Agents should work autonomously: make the requested change, validate it, commit
  it, push the branch, and open a pull request when the change is ready for review.

## Pull Request Review

- The authoring agent should self-check its work before opening a pull request.
- The authoring agent should not be the final reviewer of its own pull request.
- Use an independent reviewer when possible: another agent, a maintainer, or a
  human reviewer.
- Reviews should check scope, clarity, validation evidence, and whether
  `features/` remains untouched unless a feature-specification change was
  explicitly requested.
- Documentation-only changes can have lightweight review, but review should still
  be independent.

## Validation

- At minimum, review changed `.feature` files for clear syntax and readable flow.
- If a runner or lint command exists, run it before claiming completion.
- Do not invent passing test evidence. Report exactly what was and was not run.
- When validation is not available, state that clearly and describe the manual
  checks performed.

## Implementation Metrics

When a project is generated or implemented from these specifications, include a
final implementation summary with measurable results.

Default report location:

- Write the final implementation summary to
  `docs/implementation/FINAL_IMPLEMENTATION_REPORT.md` in the generated or
  implemented project.
- If the project already has a different established reporting location, use that
  location and link it from the pull request.
- Do not place implementation reports under `features/`.

Required metrics:

- Number of feature files consumed.
- Number of scenarios identified.
- Number of scenarios implemented or mapped.
- Number of scenarios not implemented, if any.
- Validation commands run and their results.
- Test count, pass count, fail count, and skipped count when available.
- Known gaps, assumptions, deferred requirements, or unsupported scenarios.

Report when available:

- Feature-to-scenario and scenario-to-implementation mapping.
- Files generated and files manually edited after generation.
- Components, modules, endpoints, commands, screens, or workflows created.
- Unit, integration, end-to-end, contract, or Gherkin scenario execution counts.
- Lint, typecheck, build, and runtime smoke-test results.
- Files changed and lines added or removed.
- Generated versus manually authored file counts, if knowable.
- Local run instructions, required environment variables, external services, and
  seed or test data requirements.
- Deployment artifacts, reports, logs, or generated documentation paths.
- AI generation audit notes, including source specifications used and assumptions
  made by the agent.

Do not invent metrics. If a metric is unavailable, state that it is unavailable
and explain why.
