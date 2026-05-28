@contract @black-box @public-routes @phase-1
Feature: Public and anonymous route contract
  The Booking service exposes health and API documentation without credentials.
  Booking API endpoints remain protected when security is enabled.

  Background:
    Given the Booking service is running with security enabled
    And no Authorization header is sent

  Scenario: Anonymous callers can read service health
    When I send a GET request to "/actuator/health"
    Then the response status should be 200
    And the response body should be JSON
    And the response body should contain a field named "status"

  Scenario: Anonymous callers can read the OpenAPI document
    When I send a GET request to "/api-docs/openapi.json"
    Then the response status should be 200
    And the response body should be JSON
    And the response body should describe the Booking API

  Scenario: Anonymous callers can open Swagger UI when it is packaged
    When I send a GET request to "/swagger-ui/index.html"
    Then the response status should be 200 or a redirect status
    And the response should not require authentication

  Scenario Outline: Anonymous callers are rejected from protected Booking API routes
    When I send a <method> request to "<path>"
    Then the response status should be 401
    And the response body should be JSON
    And the response body should contain:
      | field   | value                                               |
      | status  | 401                                                 |
      | error   | Unauthorized                                        |
      | message | Authentication is required to access this resource |
      | path    | <path>                                              |

    Examples:
      | method | path                               |
      | POST   | /api/v1/bookings                   |
      | GET    | /api/v1/bookings                   |
      | GET    | /api/v1/bookings/42                |
      | PATCH  | /api/v1/bookings/42/cancel         |
      | PATCH  | /api/v1/bookings/42/confirm        |
      | PATCH  | /api/v1/bookings/42/start          |
      | PATCH  | /api/v1/bookings/42/complete       |

  Scenario: Unknown API paths return the structured error contract
    When I send a GET request to "/api/v1/unknown-route"
    Then the response status should be 404
    And the response body should be JSON
    And the response body should contain:
      | field   | value                                             |
      | status  | 404                                               |
      | error   | Not Found                                         |
      | message | No endpoint found for GET /api/v1/unknown-route |
      | path    | /api/v1/unknown-route                            |
    And the response body should contain a field named "timestamp"
    And the response body should omit "requestId" when no request ID is sent
