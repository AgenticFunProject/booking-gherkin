# Implementation Assumptions

Record implementation assumptions here when requirements are unclear or
incomplete. Add new entries at the top so the newest assumption appears first.

Do not edit `features/` to resolve ambiguity unless a project maintainer
explicitly requests a feature-specification change.

### 2026-05-28 - Error Handling Local Failure Triggers

- Context: `features/error-handling.feature` requires local dependency behavior overrides and an unexpected internal failure, but it does not prescribe a concrete control surface for black-box tests.
- Assumption: The error-handling slice uses HTTP headers as deterministic local-profile triggers: `X-Local-Dependency-Behavior` for schedule, quote, and equipment-reservation failures, and `X-Trigger-Internal-Failure` for sanitized 500 responses.
- Risk: Later integration slices may replace these header hooks with client stubs, test configuration, or failure-injection fixtures.
- Affected feature files or scenarios: `features/error-handling.feature` scenarios "Business and integration exception statuses stay stable" and "Unexpected server errors hide internal details".
- Follow-up needed: Revisit these hooks when full external client integrations and resilience behavior are implemented.

### 2026-05-28 - Empty Equipment Error Classification

- Context: `features/booking-create.feature` describes the empty equipment list rejection as a validation failure, while `features/error-handling.feature` lists the same case under business validation errors without field-level `violations`.
- Assumption: The error-handling slice keeps the rejection status and message but classifies an empty equipment list as a business `400 Bad Request` without `violations`, matching the more specific error-body contract.
- Risk: A future Cucumber runner may need a shared definition of "validation failure" that accepts this business error shape, or the feature files may need maintainer clarification.
- Affected feature files or scenarios: `features/booking-create.feature` scenario "Empty equipment list is rejected"; `features/error-handling.feature` scenario outline "Business validation errors return Bad Request without field violations".
- Follow-up needed: Confirm the intended classification before implementing a full end-to-end contract runner.

### 2026-05-28 - Local Clients Use Lifecycle Endpoint Mapping

- Context: `features/local-clients.feature` requires clients to confirm, cancel, and retrieve the latest booking by id, but it does not define concrete lifecycle HTTP paths. The lifecycle implementation merged first and defines the authoritative id-based lifecycle API.
- Assumption: This local-clients slice uses the merged lifecycle API: `POST /api/v1/bookings/{bookingId}/confirm`, `POST /api/v1/bookings/{bookingId}/cancel`, and `GET /api/v1/bookings/{bookingId}`.
- Risk: Later API versioning or lifecycle changes may require the local-clients contract tests to follow the authoritative lifecycle surface again.
- Affected feature files or scenarios: `features/local-clients.feature` scenarios "Confirming a booking uses local equipment reservation behavior", "Cancelling a confirmed booking uses local equipment release behavior", and "Real external HTTP services are outside this local contract".
- Follow-up needed: Keep local equipment reserve/release behavior attached to the authoritative lifecycle transitions when future lifecycle changes land.

### 2026-05-28 - Read/List Equipment Fixture Clarification

- Context: `features/booking-read-list.feature` seeds an existing completed booking with equipment type `40FT`, while the first slice only derived an allowlist from create scenarios.
- Assumption: `40FT` is now treated as a supported equipment type for generated app behavior; `45FT` remains unsupported because `features/booking-create.feature` explicitly rejects it.
- Risk: A future canonical equipment catalog may replace this incremental allowlist.
- Affected feature files or scenarios: `features/booking-read-list.feature` Background seeded bookings and `features/booking-create.feature` scenario "Unsupported equipment type is rejected".
- Follow-up needed: Replace the incremental allowlist when a broader equipment-catalog feature or domain model is specified.

### 2026-05-28 - First Slice REST And Storage Mapping

- Context: `features/booking-create.feature` specifies client booking creation and retrieval behavior but does not name concrete HTTP paths or persistence technology for the first implementation slice.
- Assumption: The contract is mapped to `POST /bookings` for creation and `GET /bookings/{bookingReference}` for retrieval, with in-memory persistence for this first slice because durable persistence is covered by later feature files.
- Risk: Later slices may require path changes, authentication-aware routing, or durable storage migrations.
- Affected feature files or scenarios: `features/booking-create.feature` scenarios "Create a booking with multiple equipment lines" and "Created booking details preserve customer, cargo, and equipment fields".
- Follow-up needed: Revisit endpoint shape and persistence when implementing read/list, lifecycle, ownership, and persistence-runtime feature slices.

### 2026-05-28 - First Slice Equipment Type Allowlist

- Context: `features/booking-create.feature` accepts `20FT`, `40HC`, and `REEFER` examples and explicitly rejects `45FT`, but does not provide a complete equipment type catalog.
- Assumption: The first slice supports `20FT`, `40HC`, and `REEFER`; all other equipment types are rejected with a bad request response.
- Risk: A later canonical catalog may add valid equipment types.
- Affected feature files or scenarios: `features/booking-create.feature` scenarios "Local schedule and quote stubs accept otherwise valid booking requests" and "Unsupported equipment type is rejected".
- Follow-up needed: Replace the first-slice allowlist if a broader equipment catalog is specified.

## Template

### YYYY-MM-DD - Short Assumption Title

- Context:
- Assumption:
- Risk:
- Affected feature files or scenarios:
- Follow-up needed:
