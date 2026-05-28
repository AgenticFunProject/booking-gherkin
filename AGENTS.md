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

## Validation

- At minimum, review changed `.feature` files for clear syntax and readable flow.
- If a runner or lint command exists, run it before claiming completion.
- Do not invent passing test evidence. Report exactly what was and was not run.
- When validation is not available, state that clearly and describe the manual
  checks performed.
