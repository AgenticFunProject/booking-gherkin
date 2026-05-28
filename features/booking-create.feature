@booking @booking-create @contract @contract-phase-1 @black-box
Feature: Booking creation contract
  The booking API accepts valid cargo booking requests and exposes the created
  booking with a generated reference, initial lifecycle state, and preserved
  request details while running against local external client stubs.

  Background:
    Given the Booking Service is running with the "local" profile
    And security is disabled
    And the local schedule client stub accepts schedule validations
    And the local quote client stub accepts quote validations

  Scenario: Create a booking with multiple equipment lines
    When a client creates a booking with this request:
      """
      {
        "customerId": 3001,
        "scheduleId": 1001,
        "quoteId": 2001,
        "customer": {
          "name": "Acme Shipping Co.",
          "email": "logistics@acme.com",
          "phone": "+36-1-234-5678"
        },
        "cargo": {
          "description": "Industrial machinery parts",
          "weightKg": 12000.00
        },
        "equipment": [
          { "type": "20FT", "quantity": 2 },
          { "type": "40HC", "quantity": 1 }
        ]
      }
      """
    Then the response status is 201
    And the response content type is "application/json"
    And the response body has a numeric "id"
    And the response body has "customerId" equal to 3001
    And the response body has "status" equal to "PENDING"
    And the response body has "bookingReference" matching "BKG-[0-9]{4}-[0-9]{5}"
    And the response body has "bookingReference" for the current UTC year
    And the response body has "createdAt" as an ISO-8601 UTC timestamp
    And the latest booking reference is saved for this scenario

  Scenario: Created booking details preserve customer, cargo, and equipment fields
    Given a booking was created with this request:
      """
      {
        "customerId": 3001,
        "scheduleId": 1001,
        "quoteId": 2001,
        "customer": {
          "name": "Acme Shipping Co.",
          "email": "logistics@acme.com",
          "phone": "+36-1-234-5678"
        },
        "cargo": {
          "description": "Industrial machinery parts",
          "weightKg": 12000.00
        },
        "equipment": [
          { "type": "20FT", "quantity": 2 },
          { "type": "40HC", "quantity": 1 }
        ]
      }
      """
    When the client retrieves the latest booking by booking reference
    Then the response status is 200
    And the response body has "bookingReference" equal to the latest booking reference
    And the response body has "status" equal to "PENDING"
    And the response body has "customerId" equal to 3001
    And the response body has "scheduleId" equal to 1001
    And the response body has "quoteId" equal to 2001
    And the response body has "customer.name" equal to "Acme Shipping Co."
    And the response body has "customer.email" equal to "logistics@acme.com"
    And the response body has "customer.phone" equal to "+36-1-234-5678"
    And the response body has "cargo.description" equal to "Industrial machinery parts"
    And the response body has "cargo.weightKg" equal to 12000.00
    And the response body has "equipment" containing exactly:
      | type | quantity |
      | 20FT | 2        |
      | 40HC | 1        |

  Scenario: Local schedule and quote stubs accept otherwise valid booking requests
    When a client creates a booking with this request:
      """
      {
        "customerId": 3002,
        "scheduleId": 987654321,
        "quoteId": 876543210,
        "customer": {
          "name": "Stub Accepted Customer",
          "email": "stub.accepted@example.com",
          "phone": "+36-1-555-0100"
        },
        "cargo": {
          "description": "Cargo accepted by local stubs",
          "weightKg": 42.50
        },
        "equipment": [
          { "type": "REEFER", "quantity": 1 }
        ]
      }
      """
    Then the response status is 201
    And the response body has "status" equal to "PENDING"
    And the response body has "bookingReference" matching "BKG-[0-9]{4}-[0-9]{5}"

  Scenario Outline: Missing required fields are rejected
    When a client creates a booking with this request:
      """
      <request>
      """
    Then the response status is 400
    And the response content type is "application/json"
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body describes a validation failure for "<field>"

    Examples:
      | field                 | request                                                                                                                                                                                                                                                                                              |
      | customerId            | { "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }                  |
      | scheduleId            | { "customerId": 3001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }                  |
      | quoteId               | { "customerId": 3001, "scheduleId": 1001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }              |
      | customer.name         | { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }                     |
      | customer.email        | { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }                      |
      | cargo.description     | { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }                                      |
      | cargo.weightKg        | { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts" }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }                |
      | equipment[0].type     | { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "quantity": 2 } ] }          |
      | equipment[0].quantity | { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT" } ] }        |

  Scenario: Unsupported equipment type is rejected
    When a client creates a booking with this request:
      """
      {
        "customerId": 3001,
        "scheduleId": 1001,
        "quoteId": 2001,
        "customer": {
          "name": "Acme Shipping Co.",
          "email": "logistics@acme.com",
          "phone": "+36-1-234-5678"
        },
        "cargo": {
          "description": "Industrial machinery parts",
          "weightKg": 12000.00
        },
        "equipment": [
          { "type": "45FT", "quantity": 1 }
        ]
      }
      """
    Then the response status is 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" containing "Unsupported equipment type: 45FT"

  Scenario: Empty equipment list is rejected
    When a client creates a booking with this request:
      """
      {
        "customerId": 3001,
        "scheduleId": 1001,
        "quoteId": 2001,
        "customer": {
          "name": "Acme Shipping Co.",
          "email": "logistics@acme.com",
          "phone": "+36-1-234-5678"
        },
        "cargo": {
          "description": "Industrial machinery parts",
          "weightKg": 12000.00
        },
        "equipment": []
      }
      """
    Then the response status is 400
    And the response body has "error" equal to "Bad Request"
    And the response body describes a validation failure for "equipment"
