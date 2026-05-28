@auth @ownership @security @contract @contract-phase-2 @black-box
Feature: Authentication, roles, and booking ownership contract
  The Booking API protects booking operations with JWT authentication, role
  permissions, and customer ownership checks while preserving local unsecured
  behavior for development runs.

  Background:
    Given the Booking Service is running with security enabled
    And JWT tokens are expected to have issuer "test-issuer"
    And JWT tokens are expected to have audience "equipments-service"
    And schedule, quote, and equipment dependencies accept the default booking fixture

  Scenario Outline: Invalid or absent JWT credentials are rejected from protected booking routes
    Given the request uses <credentials>
    When the caller sends a GET request to "/api/v1/bookings"
    Then the response status must be 401
    And the response body must be JSON
    And the response body must contain:
      | field   | value                                               |
      | status  | 401                                                 |
      | error   | Unauthorized                                        |
      | message | Authentication is required to access this resource |
      | path    | /api/v1/bookings                                   |

    Examples:
      | credentials                                      |
      | no Authorization header                          |
      | an Authorization header without a Bearer token    |
      | a malformed Bearer token                         |
      | an expired Bearer token                          |
      | a Bearer token signed with the wrong key          |
      | a Bearer token with issuer "wrong-issuer"        |
      | a Bearer token with audience "other-service"     |

  Scenario: Customer tokens can create, list, read, and cancel their own bookings
    Given a customer JWT contains role "CUSTOMER" and customerId 3001
    When the customer creates a booking with customerId 3001
    Then the response status must be 201
    And the response body customerId must be 3001
    And the response body status must be "PENDING"
    And the latest booking id is saved for this scenario
    When the customer lists bookings with customerId 3001
    Then the response status must be 200
    And every returned booking must have customerId 3001
    When the customer retrieves the latest booking by id
    Then the response status must be 200
    And the response body customerId must be 3001
    When the customer cancels the latest booking
    Then the response status must be 200
    And the response body customerId must be 3001
    And the response body status must be "CANCELLED"

  Scenario: Customer ownership checks reject access to another customer's data
    Given customer 3001 has an existing "PENDING" booking
    And customer 3002 has an existing "PENDING" booking
    And a customer JWT contains role "CUSTOMER" and customerId 3001
    When the customer creates a booking with customerId 3002
    Then the response status must be 403
    And the response body error must be "Forbidden"
    When the customer lists bookings with customerId 3002
    Then the response status must be 403
    And the response body error must be "Forbidden"
    When the customer retrieves customer 3002's booking by id
    Then the response status must be 403
    And the response body error must be "Forbidden"
    When the customer cancels customer 3002's booking
    Then the response status must be 403
    And the response body error must be "Forbidden"

  Scenario: Customer list requests require an explicit matching customerId query parameter
    Given a customer JWT contains role "CUSTOMER" and customerId 3001
    When the customer lists bookings without a customerId query parameter
    Then the response status must be 400
    And the response body error must be "Bad Request"
    And the response body message must contain "customerId query parameter is required"
    When the customer lists bookings with customerId 3002
    Then the response status must be 403
    And the response body error must be "Forbidden"
    When the customer lists bookings with customerId 3001
    Then the response status must be 200

  Scenario Outline: Customer tokens without customer identity claims cannot pass ownership checks
    Given a customer JWT contains role "CUSTOMER" and no customerId or customer_id claim
    When the customer attempts to <action>
    Then the response status must be 403
    And the response body error must be "Forbidden"

    Examples:
      | action                                      |
      | create a booking with customerId 3001       |
      | list bookings with customerId 3001          |
      | list bookings without customerId            |
      | retrieve an existing booking by id          |
      | cancel an existing pending booking by id    |

  Scenario: Customer identity can be supplied with the snake_case customer_id JWT claim
    Given a customer JWT contains role "CUSTOMER" and customer_id 3001
    And customer 3001 has an existing "PENDING" booking
    When the customer retrieves the existing booking by id
    Then the response status must be 200
    And the response body customerId must be 3001

  Scenario: Operators can view all bookings and perform lifecycle transitions
    Given a caller JWT contains role "OPERATOR"
    And customer 3001 has an existing "PENDING" booking
    When the operator lists bookings without a customerId query parameter
    Then the response status must be 200
    When the operator retrieves customer 3001's booking by id
    Then the response status must be 200
    When the operator confirms customer 3001's booking
    Then the response status must be 200
    And the response body status must be "CONFIRMED"
    When the operator starts customer 3001's booking
    Then the response status must be 200
    And the response body status must be "IN_PROGRESS"
    When the operator completes customer 3001's booking
    Then the response status must be 200
    And the response body status must be "COMPLETED"

  Scenario Outline: Operators cannot create or cancel bookings
    Given a caller JWT contains role "OPERATOR"
    And customer 3001 has an existing "<status>" booking
    When the operator attempts to <action>
    Then the response status must be 403
    And the response body error must be "Forbidden"

    Examples:
      | status    | action                                |
      | PENDING   | create a booking with customerId 3001 |
      | PENDING   | cancel customer 3001's booking        |
      | CONFIRMED | cancel customer 3001's booking        |

  Scenario: Admins have full booking and protected actuator access
    Given a caller JWT contains role "ADMIN"
    When the admin creates a booking with customerId 9001
    Then the response status must be 201
    And the response body customerId must be 9001
    And the latest booking id is saved for this scenario
    When the admin lists bookings without a customerId query parameter
    Then the response status must be 200
    When the admin retrieves the latest booking by id
    Then the response status must be 200
    When the admin cancels the latest booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    When the admin creates a booking with customerId 9002
    Then the response status must be 201
    And the latest booking id is saved for this scenario
    When the admin confirms the latest booking
    Then the response status must be 200
    And the response body status must be "CONFIRMED"
    When the admin starts the latest booking
    Then the response status must be 200
    And the response body status must be "IN_PROGRESS"
    When the admin completes the latest booking
    Then the response status must be 200
    And the response body status must be "COMPLETED"
    When the admin sends a GET request to "/actuator/metrics"
    Then the response status must be 200

  Scenario: Service callers can create, read, list, and cancel on behalf of customers
    Given a caller JWT contains role "SERVICE"
    When the service caller creates a booking with customerId 7001
    Then the response status must be 201
    And the response body customerId must be 7001
    And the latest booking id is saved for this scenario
    When the service caller lists bookings without a customerId query parameter
    Then the response status must be 200
    When the service caller lists bookings with customerId 7001
    Then the response status must be 200
    And every returned booking must have customerId 7001
    When the service caller retrieves the latest booking by id
    Then the response status must be 200
    And the response body customerId must be 7001
    When the service caller cancels the latest booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"

  Scenario Outline: Service callers cannot perform operator lifecycle transitions
    Given a caller JWT contains role "SERVICE"
    And customer 7001 has an existing "<status>" booking
    When the service caller attempts to <action>
    Then the response status must be 403
    And the response body error must be "Forbidden"

    Examples:
      | status      | action                         |
      | PENDING     | confirm the booking            |
      | CONFIRMED   | start the booking              |
      | IN_PROGRESS | complete the booking           |

  Scenario: Local unsecured mode permits booking requests without JWT ownership checks
    Given the Booking Service is running with security disabled
    And no Authorization header is sent
    When a local caller creates a booking with customerId 3001
    Then the response status must be 201
    And the response body customerId must be 3001
    When the local caller lists bookings without a customerId query parameter
    Then the response status must be 200
