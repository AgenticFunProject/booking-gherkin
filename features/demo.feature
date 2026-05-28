@booking @demo @contract @contract-phase-4 @black-box
Feature: Booking demo contract
  A coworker can run the Booking Service locally, inspect the public API
  surface, create a representative booking, and walk that booking through the
  successful shipment lifecycle using only public HTTP behavior.

  Background:
    Given the Booking Service is running with the "local" profile
    And security is disabled
    And no Schedule API HTTP service is required to be running
    And no Quote API HTTP service is required to be running
    And no Equipment API HTTP service is required to be running
    And the local schedule client stub accepts schedule validations
    And the local quote client stub accepts quote validations
    And the local equipment client stub accepts reservation and release actions

  Scenario: Start from an inspectable local service with no demo bookings
    Given no bookings exist for customer 4201
    When a client opens Swagger UI at "/swagger-ui/index.html"
    Then the response status is 200 or a redirect status
    And the response should not require authentication
    When a client retrieves the OpenAPI document from "/api-docs/openapi.json"
    Then the response status is 200
    And the response body should describe the Booking API
    When a client lists bookings with query parameters:
      | customerId | 4201 |
      | page       | 0    |
      | size       | 20   |
      | sort       | createdAt,desc |
    Then the response status is 200
    And the response body has "content" equal to an empty array
    And the response body has "page" equal to 0
    And the response body has "size" equal to 20
    And the response body has "totalElements" equal to 0
    And the response body has "last" equal to true

  Scenario: Demonstrate create, read, list, and complete lifecycle behavior
    When a client creates a booking with this request:
      """
      {
        "customerId": 4201,
        "scheduleId": 91001,
        "quoteId": 92001,
        "customer": {
          "name": "Demo Logistics Ltd.",
          "email": "demo.ops@example.com",
          "phone": "+36-1-555-4201"
        },
        "cargo": {
          "description": "Demo cargo: packaged industrial components",
          "weightKg": 1250.50
        },
        "equipment": [
          { "type": "20FT", "quantity": 1 },
          { "type": "40HC", "quantity": 1 }
        ]
      }
      """
    Then the response status is 201
    And the response content type is "application/json"
    And the response body has a numeric "id"
    And the response body has "customerId" equal to 4201
    And the response body has "status" equal to "PENDING"
    And the response body has "bookingReference" matching "BKG-[0-9]{4}-[0-9]{5}"
    And the latest booking id is saved for this scenario
    And the latest booking reference is saved for this scenario
    When the client retrieves the latest booking by booking reference
    Then the response status is 200
    And the response body has "bookingReference" equal to the latest booking reference
    And the response body has "status" equal to "PENDING"
    And the response body has "customerId" equal to 4201
    And the response body has "scheduleId" equal to 91001
    And the response body has "quoteId" equal to 92001
    And the response body has "customer.name" equal to "Demo Logistics Ltd."
    And the response body has "customer.email" equal to "demo.ops@example.com"
    And the response body has "customer.phone" equal to "+36-1-555-4201"
    And the response body has "cargo.description" equal to "Demo cargo: packaged industrial components"
    And the response body has "cargo.weightKg" equal to 1250.50
    And the response body has "equipment" containing exactly:
      | type | quantity |
      | 20FT | 1        |
      | 40HC | 1        |
    When a client lists bookings with query parameters:
      | customerId | 4201 |
      | page       | 0    |
      | size       | 20   |
      | sort       | createdAt,desc |
    Then the response status is 200
    And the response body has "content" containing 1 booking
    And every booking in "content" has "customerId" equal to 4201
    When the client confirms the latest booking
    Then the response status is 200
    And the response body has "status" equal to "CONFIRMED"
    When the client starts the latest booking
    Then the response status is 200
    And the response body has "status" equal to "IN_PROGRESS"
    When the client completes the latest booking
    Then the response status is 200
    And the response body has "status" equal to "COMPLETED"
    When the client retrieves the latest booking by id
    Then the response status is 200
    And the response body has "bookingReference" equal to the latest booking reference
    And the response body has "status" equal to "COMPLETED"
    And the response body has "customer.name" equal to "Demo Logistics Ltd."
    And the response body has "cargo.description" equal to "Demo cargo: packaged industrial components"
    And the response body has "equipment" containing exactly:
      | type | quantity |
      | 20FT | 1        |
      | 40HC | 1        |
