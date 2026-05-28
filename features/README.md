# Booking Gherkin Contract

The files in `test/features` are the black-box acceptance contract for the
Booking Service. They describe externally visible behavior that any replacement
implementation must preserve, regardless of framework, persistence technology,
package layout, or internal class names.

This contract is written from the point of view of HTTP clients, operators, and
local developers. A passing implementation exposes the same public routes,
request and response shapes, status transitions, authorization outcomes,
structured errors, local stub behavior, and observable runtime persistence.

## Feature Coverage Map

| Feature file | Externally meaningful behavior |
| --- | --- |
| `public-routes.feature` | Public API surface, health/docs routes, HTTP methods, path conventions, and unsupported-route behavior. |
| `booking-create.feature` | Booking creation through `POST /api/v1/bookings`, required request data, customer/cargo/equipment validation, generated references, and `PENDING` creation responses. |
| `booking-read-list.feature` | Lookup by numeric ID or `BKG-YYYY-NNNNN` reference, paginated listing, customer/status filters, and empty-result behavior. |
| `booking-lifecycle.feature` | Operator-managed `PENDING -> CONFIRMED -> IN_PROGRESS -> COMPLETED` lifecycle behavior and rejection of invalid forward or terminal transitions. |
| `booking-cancellation.feature` | Customer/service cancellation for `PENDING` and `CONFIRMED` bookings, terminal cancellation behavior, and cancellation outcomes when local equipment release fails. |
| `auth-ownership.feature` | JWT role behavior, customer ownership checks, service/operator/admin visibility, disabled-security local mode, and public documentation/health access. |
| `error-handling.feature` | Stable error response format, validation violations, request IDs, business exception HTTP statuses, and non-leakage of internal failures. |
| `local-clients.feature` | Local schedule, quote, and equipment client stub behavior used for development and acceptance runs before real external contracts exist. |
| `persistence-runtime.feature` | Observable persistence behavior across create/read/list/lifecycle operations, reference uniqueness, pagination stability, and restart-safe data visibility. |
| `demo.feature` | Coworker-facing demo flow covering local startup, create/read/list/lifecycle/cancel examples, Swagger/OpenAPI access, and local profile expectations. |

Feature files may be added by later beads. Until a listed file exists, this map
is the planned contract boundary for that bead.

## Replacement Implementation Rules

A replacement Booking implementation satisfies this contract when it can run the
same scenarios against a deployed service through public HTTP interfaces and
produce equivalent externally observable results.

Replacement implementations may change:

- Java, Spring, Maven, JPA, Flyway, or database internals.
- Package names, class names, repository shapes, service method names, and bean
  configuration.
- Internal state-machine representation, validation helper structure, logging
  implementation, and client wiring.
- Database schema details, SQL, migrations, indexes, and generated primary-key
  strategy, provided public behavior stays equivalent.

Replacement implementations must preserve:

- `/api/v1` route behavior and documented HTTP methods.
- Public request and response JSON fields, status codes, and error response
  structure.
- Booking reference format, lifecycle vocabulary, and allowed transitions.
- Role and ownership outcomes when security is enabled, plus documented local
  behavior when security is disabled.
- Local stub behavior for external services until real external API contracts
  are available.

## Explicit Exclusions

The contract must not assert implementation internals, including:

- Java package names, annotations, constructors, repository interfaces, entity
  mappings, Flyway migration contents, or Spring bean names.
- Exact SQL statements, table names, indexes, transaction boundaries, lazy/eager
  loading choices, or ORM behavior.
- JWT library internals, filter ordering beyond observable authorization
  results, or token parsing implementation details.
- Log message wording, log framework configuration, metrics internals, or
  actuator component details beyond documented public health/info/metrics
  access rules.
- Direct database reads or writes from scenarios, except where a runtime
  persistence scenario observes behavior through the public API after service
  restart or repeated requests.

## Local External Client Scope

The Booking Service currently owns only the client interfaces and local stub
behavior for Schedule, Equipment, and Quote interactions. Real external service
contracts are out of scope until the owning teams publish stable APIs.

The Gherkin contract may require that local acceptance runs can create,
confirm, cancel, and complete bookings using local stubs. It must not require
specific remote Schedule, Equipment, or Quote endpoints, payloads, retry rules,
WireMock mappings, or health indicators for those services.

When real external contracts become available, add new feature coverage or
runner fixtures without changing the local-stub contract unless public Booking
behavior changes.

## Runner Expectations

The feature files are executable-contract candidates. The runner design is
documented in `RUNNER.md`; executable glue and CI wiring remain separate
follow-up work. The runner must keep these expectations:

- Run scenarios against the service as a black-box HTTP target, not by invoking
  Java classes, repositories, or test-only helpers directly.
- Use deterministic test data and isolate created bookings so scenarios do not
  depend on execution order.
- Start from a documented local profile with external client stubs unless a
  scenario explicitly targets secured behavior.
- Validate JSON through public fields and stable formats; avoid brittle checks
  for generated internal IDs except where the API returns them for follow-up
  route calls.
- Treat real external integrations, Pact-style provider contracts, and CI
  wiring as follow-up work until the relevant beads add those capabilities.
