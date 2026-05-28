@booking @booking-cancellation @contract @contract-phase-3 @black-box @cancellation
Feature: Booking cancellation contract
  The Booking API allows authorized customer, service, and admin callers to
  cancel eligible bookings while enforcing ownership and preserving cancellation
  behavior when local equipment release succeeds or fails.

  Background:
    Given the Booking Service is running with security enabled
    And schedule, quote, and equipment dependencies accept the default booking fixture
    And the default cancellation booking fixture contains:
      | field                 | value                     |
      | customerId            | 3001                      |
      | scheduleId            | 1001                      |
      | quoteId               | 2001                      |
      | customer.name         | Cancellation Customer     |
      | customer.email        | cancel.owner@example.com  |
      | customer.phone        | +36-1-555-0140            |
      | cargo.description     | Cancellation cargo        |
      | cargo.weightKg        | 1000.00                   |
      | equipment[0].type     | 20FT                      |
      | equipment[0].quantity | 1                         |

  Scenario: Customer cancels their own pending booking
    Given a customer JWT contains role "CUSTOMER" and customerId 3001
    And the customer has created a booking with the default cancellation booking fixture
    And the booking status is "PENDING"
    When the customer cancels the latest booking
    Then the response status must be 200
    And the response body customerId must be 3001
    And the response body status must be "CANCELLED"
    When the customer retrieves the latest booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"

  Scenario: Service caller cancels a confirmed booking on behalf of a customer
    Given a caller JWT contains role "SERVICE"
    And customer 3001 has an existing "CONFIRMED" booking using the default cancellation booking fixture
    And the local equipment client stub accepts release requests
    When the service caller cancels customer 3001's booking
    Then the response status must be 200
    And the response body customerId must be 3001
    And the response body status must be "CANCELLED"
    And the local equipment client stub must have received a release request for the booking
    When the service caller retrieves the booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"

  Scenario: Admin cancels a booking for any customer
    Given a caller JWT contains role "ADMIN"
    And customer 9001 has an existing "PENDING" booking using the default cancellation booking fixture
    When the admin cancels customer 9001's booking
    Then the response status must be 200
    And the response body customerId must be 9001
    And the response body status must be "CANCELLED"

  Scenario: Customer cannot cancel another customer's booking
    Given customer 3001 has an existing "PENDING" booking using the default cancellation booking fixture
    And customer 3002 has an existing "PENDING" booking using the default cancellation booking fixture
    And a customer JWT contains role "CUSTOMER" and customerId 3001
    When the customer cancels customer 3002's booking
    Then the response status must be 403
    And the response body error must be "Forbidden"
    When a privileged caller retrieves customer 3002's booking by id
    Then the response status must be 200
    And the response body customerId must be 3002
    And the response body status must be "PENDING"

  Scenario: Pending cancellation does not request equipment release
    Given a caller JWT contains role "ADMIN"
    And customer 3001 has an existing "PENDING" booking using the default cancellation booking fixture
    And the local equipment client stub is observing release requests
    When the admin cancels customer 3001's booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    And the local equipment client stub must not have received a release request for the booking

  Scenario: Confirmed cancellation releases equipment before returning cancelled
    Given a caller JWT contains role "ADMIN"
    And customer 3001 has an existing "CONFIRMED" booking using the default cancellation booking fixture
    And the local equipment client stub accepts release requests
    When the admin cancels customer 3001's booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    And the local equipment client stub must have received a release request for the booking
    When the admin retrieves customer 3001's booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"

  Scenario: Confirmed cancellation remains successful when local equipment release fails
    Given a caller JWT contains role "ADMIN"
    And customer 3001 has an existing "CONFIRMED" booking using the default cancellation booking fixture
    And the local equipment client stub is configured to fail release requests for the booking
    When the admin cancels customer 3001's booking
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    And the response body error must be absent
    When the admin retrieves customer 3001's booking by id
    Then the response status must be 200
    And the response body status must be "CANCELLED"
    And the local equipment client stub must have recorded the failed release attempt for the booking

  Scenario Outline: Ineligible bookings cannot be cancelled
    Given a caller JWT contains role "ADMIN"
    And customer 3001 has an existing "<status>" booking using the default cancellation booking fixture
    When the admin cancels customer 3001's booking
    Then the response status must be 409
    And the response body error must be "Conflict"
    And the response body message must mention "<status> to CANCELLED"
    When the admin retrieves customer 3001's booking by id
    Then the response status must be 200
    And the response body status must be "<status>"

    Examples:
      | status      |
      | IN_PROGRESS |
      | COMPLETED   |
      | CANCELLED   |
