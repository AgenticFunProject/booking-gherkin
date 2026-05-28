@error-handling @contract @contract-phase-2 @black-box @errors
Feature: Booking error handling contract
  The Booking API returns stable HTTP status codes and structured JSON error
  bodies so clients can handle validation, business, security, and routing
  failures consistently.

  Background:
    Given the Booking Service is running with the "local" profile
    And schedule, quote, and equipment dependencies use local stubs unless a scenario overrides them
    And the default valid booking request is:
      """
      {
        "customerId": 3001,
        "scheduleId": 1001,
        "quoteId": 2001,
        "customer": {
          "name": "Error Contract Customer",
          "email": "errors@example.com",
          "phone": "+36-1-555-0199"
        },
        "cargo": {
          "description": "Contract error cargo",
          "weightKg": 1000.00
        },
        "equipment": [
          { "type": "20FT", "quantity": 1 }
        ]
      }
      """

  Scenario: Every standard error body exposes stable top-level fields
    Given security is disabled
    When a client requests booking "BKG-2099-99999" with header "X-Request-ID" equal to "req-error-contract-1"
    Then the response status is 404
    And the response content type is "application/json"
    And the response body has "timestamp" as an ISO-8601 UTC timestamp
    And the response body has "status" equal to 404
    And the response body has "error" equal to "Not Found"
    And the response body has "message" containing "Booking not found"
    And the response body has "path" equal to "/api/v1/bookings/BKG-2099-99999"
    And the response body has "requestId" equal to "req-error-contract-1"

  Scenario: Bean validation failures return field-level violations
    Given security is disabled
    When a client creates a booking with this request:
      """
      {
        "customerId": 3001,
        "scheduleId": 1001,
        "quoteId": 2001,
        "customer": {
          "name": "",
          "email": "not-an-email"
        },
        "cargo": {
          "description": "",
          "weightKg": -5
        },
        "equipment": [
          { "type": "", "quantity": 0 }
        ]
      }
      """
    Then the response status is 400
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" containing "Validation failed"
    And the response body has "path" equal to "/api/v1/bookings"
    And the response body has a "violations" array
    And the response body describes validation failures for:
      | field                 |
      | cargo.description     |
      | cargo.weightKg        |
      | customer.email        |
      | customer.name         |
      | equipment[0].quantity |
      | equipment[0].type     |
    And each violation has "field", "message", and "rejectedValue" fields
    And the violations are sorted by field name

  Scenario Outline: Business validation errors return Bad Request without field violations
    Given security is disabled
    When the client triggers business validation error "<case>"
    Then the response status is 400
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" containing "<messageFragment>"
    And the response body has "path" equal to "<path>"
    And the response body does not include "violations"

    Examples:
      | case                     | path                         | messageFragment              |
      | unsupported equipment    | /api/v1/bookings             | Unsupported equipment type   |
      | empty equipment list     | /api/v1/bookings             | equipment                    |
      | invalid booking id value | /api/v1/bookings/not-a-valid | Invalid booking identifier   |

  Scenario: Malformed JSON request bodies return a sanitized Bad Request
    Given security is disabled
    When a client creates a booking with malformed JSON:
      """
      { "customerId": 3001, "scheduleId": 1001,
      """
    Then the response status is 400
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" equal to "Malformed JSON request body"
    And the response body has "path" equal to "/api/v1/bookings"
    And the response body message does not expose parser exception details

  Scenario Outline: Business and integration exception statuses stay stable
    Given security is disabled
    And the local dependency behavior is configured for "<case>"
    When a client creates or updates a booking using the default valid booking request
    Then the response status is <status>
    And the response body has "status" equal to <status>
    And the response body has "error" equal to "<error>"
    And the response body has "message" containing "<messageFragment>"
    And the response body has "path" matching "/api/v1/bookings.*"

    Examples:
      | case                            | status | error                 | messageFragment                 |
      | unavailable schedule            | 422    | Unprocessable Entity  | schedule                        |
      | invalid quote                   | 422    | Unprocessable Entity  | quote                           |
      | equipment reservation failure   | 503    | Service Unavailable   | equipment reservation           |

  Scenario: Missing bookings return Not Found
    Given security is disabled
    When a client requests booking "999999999"
    Then the response status is 404
    And the response body has "status" equal to 404
    And the response body has "error" equal to "Not Found"
    And the response body has "message" containing "Booking not found"
    And the response body has "path" equal to "/api/v1/bookings/999999999"

  Scenario: Illegal lifecycle transitions return Conflict and do not change status
    Given security is enabled
    And the caller is authorized to create and manage lifecycle transitions
    And the caller has created and completed a booking using the default valid booking request
    When the caller requests lifecycle action "cancel"
    Then the response status is 409
    And the response body has "status" equal to 409
    And the response body has "error" equal to "Conflict"
    And the response body has "message" containing "COMPLETED to CANCELLED"
    And the response body has "path" matching "/api/v1/bookings/[0-9]+/cancel"
    When the caller retrieves the booking by id
    Then the response status is 200
    And the response body has "status" equal to "COMPLETED"

  Scenario: Anonymous callers receive the structured Unauthorized response
    Given security is enabled
    And no Authorization header is sent
    When a client sends a GET request to "/api/v1/bookings"
    Then the response status is 401
    And the response content type is "application/json"
    And the response body has "timestamp" as an ISO-8601 UTC timestamp
    And the response body has "status" equal to 401
    And the response body has "error" equal to "Unauthorized"
    And the response body has "message" equal to "Authentication is required to access this resource"
    And the response body has "path" equal to "/api/v1/bookings"

  Scenario Outline: Forbidden callers receive the structured Forbidden response
    Given security is enabled
    And the caller is authenticated as "<caller>"
    When the caller sends a <method> request to "<path>"
    Then the response status is 403
    And the response body has "status" equal to 403
    And the response body has "error" equal to "Forbidden"
    And the response body has "message" equal to "You do not have permission to perform this action"
    And the response body has "path" equal to "<path>"

    Examples:
      | caller                              | method | path                        |
      | CUSTOMER without customer identity  | GET    | /api/v1/bookings?customerId=3001 |
      | CUSTOMER for another customer       | POST   | /api/v1/bookings            |
      | SERVICE on operator-only action     | PATCH  | /api/v1/bookings/42/confirm |

  Scenario: Customer list requests without a required customerId return Bad Request
    Given security is enabled
    And the caller is authenticated as a CUSTOMER with customerId 3001
    When the caller sends a GET request to "/api/v1/bookings"
    Then the response status is 400
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" containing "customerId"
    And the response body has "path" equal to "/api/v1/bookings"

  Scenario: Invalid query parameter values return Bad Request
    Given security is disabled
    When a client sends a GET request to "/api/v1/bookings?status=SHIPPED"
    Then the response status is 400
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" containing "status"
    And the response body has "message" containing "BookingStatus"
    And the response body has "path" equal to "/api/v1/bookings"

  Scenario: Unsupported methods return Method Not Allowed
    Given security is disabled
    When a client sends a DELETE request to "/api/v1/bookings/42"
    Then the response status is 405
    And the response body has "status" equal to 405
    And the response body has "error" equal to "Method Not Allowed"
    And the response body has "message" containing "DELETE"
    And the response body has "path" equal to "/api/v1/bookings/42"

  Scenario: Unknown API paths return the structured Not Found response
    Given security is disabled
    When a client sends a GET request to "/api/v1/bookings/42/unknown-action"
    Then the response status is 404
    And the response body has "status" equal to 404
    And the response body has "error" equal to "Not Found"
    And the response body has "message" equal to "No endpoint found for GET /api/v1/bookings/42/unknown-action"
    And the response body has "path" equal to "/api/v1/bookings/42/unknown-action"
    And the response body has "timestamp" as an ISO-8601 UTC timestamp
    And the response body omits "requestId" when no request ID is sent

  Scenario: Unexpected server errors hide internal details
    Given security is disabled
    And the Booking Service has an unexpected internal failure
    When a client sends a request that triggers the failure
    Then the response status is 500
    And the response body has "status" equal to 500
    And the response body has "error" equal to "Internal Server Error"
    And the response body has "message" equal to "An unexpected error occurred. Please try again later."
    And the response body does not expose stack traces or exception class names
