# Implementation Assumptions

Record implementation assumptions here when requirements are unclear or
incomplete. Add new entries at the top so the newest assumption appears first.

Do not edit `features/` to resolve ambiguity unless a project maintainer
explicitly requests a feature-specification change.

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
