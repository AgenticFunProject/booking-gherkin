@black-box @contract @contract-phase-1 @lifecycle
Feature: Booking lifecycle contract
  The Booking API must expose a strict shipment lifecycle so that callers can
  rely on deterministic status transitions and terminal booking behavior.

  Background:
    Given the Booking API is running with lifecycle endpoints available
    And the caller is authorized to create and manage lifecycle transitions
    And schedule, quote, and equipment dependencies accept the default booking fixture
    And the default booking fixture contains:
      | field             | value                           |
      | customerId        | 3001                            |
      | scheduleId        | 1001                            |
      | quoteId           | 2001                            |
      | customer.name     | Test Customer                   |
      | customer.email    | test@example.com                |
      | cargo.description | Test cargo                      |
      | cargo.weightKg    | 1000.00                         |
      | equipment[0].type | 20FT                            |
      | equipment[0].quantity | 1                               |

  Scenario: Complete a booking through the valid lifecycle path
    When the caller creates a booking with the default booking fixture
    Then the response status must be 201
    And the response body status must be "PENDING"
    And the response body must include a booking id
    And the response body booking reference must match "BKG-\d{4}-\d{5}"
    When the caller confirms the booking
    Then the response status must be 200
    And the response body status must be "CONFIRMED"
    When the caller starts the booking
    Then the response status must be 200
    And the response body status must be "IN_PROGRESS"
    When the caller completes the booking
    Then the response status must be 200
    And the response body status must be "COMPLETED"
    When the caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "COMPLETED"

  Scenario: Cancel a pending booking
    Given the caller has created a booking with the default booking fixture
    And the booking status is "PENDING"
    When the caller cancels the booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    When the caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"

  Scenario: Cancel a confirmed booking
    Given the caller has created a booking with the default booking fixture
    And the caller has confirmed the booking
    And the booking status is "CONFIRMED"
    When the caller cancels the booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    When the caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"

  Scenario Outline: Reject invalid non-terminal lifecycle transitions
    Given the caller has created a booking with the default booking fixture
    And the booking has reached status "<currentStatus>"
    When the caller requests lifecycle action "<action>"
    Then the response status must be 409
    And the response body error must be "Conflict"
    And the response body message must mention "<currentStatus> to <targetStatus>"
    When the caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "<currentStatus>"

    Examples:
      | currentStatus | action   | targetStatus |
      | PENDING       | start    | IN_PROGRESS  |
      | PENDING       | complete | COMPLETED    |
      | CONFIRMED     | confirm  | CONFIRMED    |
      | CONFIRMED     | complete | COMPLETED    |
      | IN_PROGRESS   | confirm  | CONFIRMED    |
      | IN_PROGRESS   | cancel   | CANCELLED    |

  Scenario Outline: Completed bookings are terminal
    Given the caller has created a booking with the default booking fixture
    And the caller has confirmed the booking
    And the caller has started the booking
    And the caller has completed the booking
    When the caller requests lifecycle action "<action>"
    Then the response status must be 409
    And the response body error must be "Conflict"
    And the response body message must mention "COMPLETED to <targetStatus>"
    When the caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "COMPLETED"

    Examples:
      | action   | targetStatus |
      | confirm  | CONFIRMED    |
      | start    | IN_PROGRESS  |
      | complete | COMPLETED    |
      | cancel   | CANCELLED    |

  Scenario Outline: Cancelled bookings are terminal
    Given the caller has created a booking with the default booking fixture
    And the caller has cancelled the booking
    When the caller requests lifecycle action "<action>"
    Then the response status must be 409
    And the response body error must be "Conflict"
    And the response body message must mention "CANCELLED to <targetStatus>"
    When the caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"

    Examples:
      | action   | targetStatus |
      | confirm  | CONFIRMED    |
      | start    | IN_PROGRESS  |
      | complete | COMPLETED    |
      | cancel   | CANCELLED    |
