@booking @persistence-runtime @contract @contract-phase-3 @black-box @persistence
Feature: Booking persistence runtime contract
  The Booking API preserves externally visible booking data across service
  restarts so clients can rely on created bookings, lifecycle status, generated
  references, and filtered lists without depending on storage internals.

  Background:
    Given the Booking Service is running with the "local" profile
    And security is disabled
    And the local schedule client stub accepts schedule validations
    And the local quote client stub accepts quote validations
    And the local equipment client stub accepts reservations and releases
    And the durable booking state is isolated for this contract run

  Scenario: Empty durable service remains empty after restart
    Given no bookings exist in durable runtime state
    When the Booking Service is restarted while preserving durable runtime state
    And a client lists bookings with query parameters:
      | page | 0 |
      | size | 20 |
    Then the response status is 200
    And the response body has "content" equal to an empty array
    And the response body has "page" equal to 0
    And the response body has "size" equal to 20
    And the response body has "totalElements" equal to 0
    And the response body has "totalPages" equal to 0
    And the response body has "last" equal to true

  Scenario: Created booking can be read after restart
    Given a booking was created with this request:
      """
      {
        "customerId": 3101,
        "scheduleId": 1101,
        "quoteId": 2101,
        "customer": {
          "name": "Durable Freight Co.",
          "email": "durable.freight@example.com",
          "phone": "+36-1-555-1101"
        },
        "cargo": {
          "description": "Restart-visible cargo",
          "weightKg": 2100.25
        },
        "equipment": [
          { "type": "20FT", "quantity": 1 },
          { "type": "REEFER", "quantity": 1 }
        ]
      }
      """
    And the created booking id and booking reference are saved for this scenario
    When the Booking Service is restarted while preserving durable runtime state
    And the client retrieves the saved booking by booking reference
    Then the response status is 200
    And the response body has "id" equal to the saved booking id
    And the response body has "bookingReference" equal to the saved booking reference
    And the response body has "status" equal to "PENDING"
    And the response body has "customerId" equal to 3101
    And the response body has "scheduleId" equal to 1101
    And the response body has "quoteId" equal to 2101
    And the response body has "customer.name" equal to "Durable Freight Co."
    And the response body has "customer.email" equal to "durable.freight@example.com"
    And the response body has "customer.phone" equal to "+36-1-555-1101"
    And the response body has "cargo.description" equal to "Restart-visible cargo"
    And the response body has "cargo.weightKg" equal to 2100.25
    And the response body has "equipment" containing exactly:
      | type   | quantity |
      | 20FT   | 1        |
      | REEFER | 1        |
    And the response body has "createdAt" as an ISO-8601 UTC timestamp
    And the response body has "updatedAt" as an ISO-8601 UTC timestamp

  Scenario Outline: Lifecycle status survives restart
    Given a booking was created for customer <customerId>
    And the booking has reached status "<status>"
    When the Booking Service is restarted while preserving durable runtime state
    And the client retrieves the booking by booking reference
    Then the response status is 200
    And the response body has "status" equal to "<status>"
    And the response body has "customerId" equal to <customerId>
    And the response body has "bookingReference" equal to the saved booking reference

    Examples:
      | customerId | status      |
      | 3201       | CONFIRMED   |
      | 3202       | IN_PROGRESS |
      | 3203       | COMPLETED   |
      | 3204       | CANCELLED   |

  Scenario: Booking references remain unique after restart
    Given a booking was created for customer 3301
    And the created booking reference is saved as "reference-before-restart"
    When the Booking Service is restarted while preserving durable runtime state
    And a booking is created for customer 3302
    Then the response status is 201
    And the response body has "bookingReference" matching "BKG-[0-9]{4}-[0-9]{5}"
    And the response body has "bookingReference" for the current UTC year
    And the response body "bookingReference" is not equal to saved reference "reference-before-restart"
    And the response body "bookingReference" has a sequence greater than saved reference "reference-before-restart"
    When the client retrieves saved reference "reference-before-restart"
    Then the response status is 200
    And the response body has "bookingReference" equal to saved reference "reference-before-restart"

  Scenario: Customer and status filtered lists reflect durable state after restart
    Given these bookings exist:
      | alias                 | customerId | scheduleId | quoteId | status      | customer.name        | customer.email                | cargo.description       | cargo.weightKg | equipment[0].type | equipment[0].quantity |
      | durable-acme-pending  | 3401       | 1401       | 2401    | PENDING     | Durable Acme         | durable.acme@example.com      | Pending durable cargo   | 100.00         | 20FT              | 1                     |
      | durable-acme-complete | 3401       | 1402       | 2402    | COMPLETED   | Durable Acme         | durable.acme@example.com      | Complete durable cargo  | 200.00         | 40FT              | 1                     |
      | durable-globex-open   | 3402       | 1403       | 2403    | PENDING     | Durable Globex       | durable.globex@example.com    | Other customer cargo    | 300.00         | REEFER            | 1                     |
      | durable-initech-done  | 3403       | 1404       | 2404    | COMPLETED   | Durable Initech      | durable.initech@example.com   | Completed other cargo   | 400.00         | 40HC              | 1                     |
    When the Booking Service is restarted while preserving durable runtime state
    And a client lists bookings with query parameters:
      | customerId | 3401      |
      | status     | COMPLETED |
      | page       | 0         |
      | size       | 20        |
      | sort       | createdAt,desc |
    Then the response status is 200
    And the response body has "content" containing 1 booking
    And the response body includes booking "durable-acme-complete"
    And the response body excludes booking "durable-acme-pending"
    And the response body excludes booking "durable-globex-open"
    And the response body excludes booking "durable-initech-done"
    And every booking in "content" has "customerId" equal to 3401
    And every booking in "content" has "status" equal to "COMPLETED"
    And the response body has "page" equal to 0
    And the response body has "size" equal to 20
    And the response body has "totalElements" equal to 1
    And the response body has "totalPages" equal to 1
    And the response body has "last" equal to true
