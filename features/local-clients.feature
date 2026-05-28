@booking @local-clients @contract @contract-phase-3 @black-box
Feature: Local external clients contract
  Booking must be runnable for local development and acceptance checks before
  real Schedule, Quote, and Equipment service contracts exist.

  Background:
    Given the Booking Service is running with the "local" profile
    And security is disabled
    And no Schedule API HTTP service is required to be running
    And no Quote API HTTP service is required to be running
    And no Equipment API HTTP service is required to be running
    And the local schedule client stub accepts schedule validations
    And the local quote client stub accepts quote validations
    And the local equipment client stub accepts reservation and release actions

  Scenario: Local schedule and quote stubs accept a valid booking request
    When a client creates a booking with this request:
      """
      {
        "customerId": 3101,
        "scheduleId": 999001,
        "quoteId": 999101,
        "customer": {
          "name": "Local Stub Customer",
          "email": "local.stub@example.com",
          "phone": "+36-1-555-3101"
        },
        "cargo": {
          "description": "Cargo accepted without real external clients",
          "weightKg": 5250.75
        },
        "equipment": [
          { "type": "20FT", "quantity": 1 },
          { "type": "REEFER", "quantity": 1 }
        ]
      }
      """
    Then the response status is 201
    And the response body has "status" equal to "PENDING"
    And the response body has "scheduleId" equal to 999001
    And the response body has "quoteId" equal to 999101
    And the response body has "bookingReference" matching "BKG-[0-9]{4}-[0-9]{5}"
    And the latest booking id is saved for this scenario
    And no outbound HTTP call to a Schedule API is required
    And no outbound HTTP call to a Quote API is required

  Scenario: Confirming a booking uses local equipment reservation behavior
    Given a booking was created with this request:
      """
      {
        "customerId": 3102,
        "scheduleId": 999002,
        "quoteId": 999102,
        "customer": {
          "name": "Local Confirm Customer",
          "email": "local.confirm@example.com",
          "phone": "+36-1-555-3102"
        },
        "cargo": {
          "description": "Cargo confirmed with local equipment stub",
          "weightKg": 800.00
        },
        "equipment": [
          { "type": "40HC", "quantity": 2 }
        ]
      }
      """
    When the client confirms the latest booking
    Then the response status is 200
    And the response body has "status" equal to "CONFIRMED"
    And no outbound HTTP call to an Equipment API is required
    When the client retrieves the latest booking by id
    Then the response status is 200
    And the response body has "status" equal to "CONFIRMED"

  Scenario: Cancelling a confirmed booking uses local equipment release behavior
    Given a booking was created with this request:
      """
      {
        "customerId": 3103,
        "scheduleId": 999003,
        "quoteId": 999103,
        "customer": {
          "name": "Local Cancel Customer",
          "email": "local.cancel@example.com",
          "phone": "+36-1-555-3103"
        },
        "cargo": {
          "description": "Cargo cancelled with local equipment release",
          "weightKg": 1200.00
        },
        "equipment": [
          { "type": "20FT", "quantity": 3 }
        ]
      }
      """
    And the client confirmed the latest booking
    When the client cancels the latest booking
    Then the response status is 200
    And the response body has "status" equal to "CANCELLED"
    And no outbound HTTP call to an Equipment API is required
    When the client retrieves the latest booking by id
    Then the response status is 200
    And the response body has "status" equal to "CANCELLED"

  Scenario: Real external HTTP services are outside this local contract
    Given the configured Schedule API base URL is unavailable
    And the configured Quote API base URL is unavailable
    And the configured Equipment API base URL is unavailable
    When a client creates, confirms, and cancels a booking using local clients
    Then each Booking API response uses the normal booking response shape
    And the booking can reach "CONFIRMED" without a real Equipment HTTP service
    And the booking can reach "CANCELLED" without a real Equipment HTTP service
    And this contract does not define Schedule, Quote, or Equipment HTTP endpoints
