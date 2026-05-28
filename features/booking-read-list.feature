@booking @booking-read-list @contract @contract-phase-2 @black-box
Feature: Booking read and list contract
  The Booking API exposes stable read and list behavior so clients can fetch a
  booking by numeric id or booking reference and page through filtered booking
  collections without relying on implementation details.

  Background:
    Given the Booking Service is running with the "local" profile
    And security is disabled
    And the local schedule client stub accepts schedule validations
    And the local quote client stub accepts quote validations
    And these bookings exist:
      | alias            | customerId | scheduleId | quoteId | status      | customer.name         | customer.email              | cargo.description     | cargo.weightKg | equipment[0].type | equipment[0].quantity |
      | acme-pending     | 3001       | 1001       | 2001    | PENDING     | Acme Shipping Co.     | logistics@acme.com         | Industrial machinery  | 12000.00       | 20FT              | 2                     |
      | acme-confirmed   | 3001       | 1002       | 2002    | CONFIRMED   | Acme Shipping Co.     | logistics@acme.com         | Replacement parts     | 3200.50        | 40HC              | 1                     |
      | globex-pending   | 3002       | 1003       | 2003    | PENDING     | Globex Logistics      | freight@globex.example     | Refrigerated cargo    | 850.25         | REEFER            | 1                     |
      | initech-complete | 3003       | 1004       | 2004    | COMPLETED   | Initech Distribution  | shipping@initech.example   | Office equipment      | 410.00         | 40FT              | 1                     |

  Scenario: Fetch a booking by numeric id
    When a client retrieves booking "acme-pending" by numeric id
    Then the response status is 200
    And the response content type is "application/json"
    And the response body has "id" equal to the id for booking "acme-pending"
    And the response body has "bookingReference" equal to the reference for booking "acme-pending"
    And the response body has "customerId" equal to 3001
    And the response body has "status" equal to "PENDING"
    And the response body has "scheduleId" equal to 1001
    And the response body has "quoteId" equal to 2001
    And the response body has "customer.name" equal to "Acme Shipping Co."
    And the response body has "customer.email" equal to "logistics@acme.com"
    And the response body has "cargo.description" equal to "Industrial machinery"
    And the response body has "cargo.weightKg" equal to 12000.00
    And the response body has "equipment" containing exactly:
      | type | quantity |
      | 20FT | 2        |
    And the response body has "createdAt" as an ISO-8601 UTC timestamp
    And the response body has "updatedAt" as an ISO-8601 UTC timestamp

  Scenario: Fetch a booking by booking reference
    When a client retrieves booking "acme-confirmed" by booking reference
    Then the response status is 200
    And the response body has "id" equal to the id for booking "acme-confirmed"
    And the response body has "bookingReference" equal to the reference for booking "acme-confirmed"
    And the response body has "bookingReference" matching "BKG-[0-9]{4}-[0-9]{5}"
    And the response body has "customerId" equal to 3001
    And the response body has "status" equal to "CONFIRMED"
    And the response body has "scheduleId" equal to 1002
    And the response body has "quoteId" equal to 2002
    And the response body has "customer.name" equal to "Acme Shipping Co."
    And the response body has "cargo.description" equal to "Replacement parts"
    And the response body has "equipment" containing exactly:
      | type | quantity |
      | 40HC | 1        |

  Scenario Outline: Missing bookings return a structured not found error
    When a client retrieves booking identifier "<identifier>"
    Then the response status is 404
    And the response content type is "application/json"
    And the response body has "status" equal to 404
    And the response body has "error" equal to "Not Found"
    And the response body has "message" containing "<messageFragment>"
    And the response body has "path" equal to "/api/v1/bookings/<identifier>"
    And the response body has "timestamp" as an ISO-8601 UTC timestamp
    And the response body omits "requestId" when no request ID is sent

    Examples:
      | identifier     | messageFragment                    |
      | 999999999      | Booking not found with id 999999999 |
      | BKG-2026-99999 | Booking not found with reference BKG-2026-99999 |

  Scenario Outline: Invalid booking identifiers are rejected before lookup
    When a client retrieves booking identifier "<identifier>"
    Then the response status is 400
    And the response content type is "application/json"
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" equal to "Invalid booking identifier: <identifier>. Expected numeric ID or booking reference in format BKG-YYYY-NNNNN"
    And the response body has "path" equal to "/api/v1/bookings/<identifier>"
    And the response body has "timestamp" as an ISO-8601 UTC timestamp

    Examples:
      | identifier       |
      | not-a-booking    |
      | BKG-2026-1234    |
      | bkg-2026-00042   |
      | BKG-26-00042     |

  Scenario: List bookings filtered by customer id
    When a client lists bookings with query parameters:
      | customerId | 3001 |
      | page       | 0    |
      | size       | 20   |
      | sort       | createdAt,desc |
    Then the response status is 200
    And the response content type is "application/json"
    And the response body has "content" containing 2 bookings
    And every booking in "content" has "customerId" equal to 3001
    And the response body includes bookings "acme-pending" and "acme-confirmed"
    And the response body excludes booking "globex-pending"
    And the response body excludes booking "initech-complete"
    And each booking in "content" includes "id", "bookingReference", "status", "scheduleId", "quoteId", "customer", "cargo", "equipment", "createdAt", and "updatedAt"

  Scenario: List bookings filtered by status
    When a client lists bookings with query parameters:
      | status | PENDING |
      | page   | 0       |
      | size   | 20      |
      | sort   | createdAt,desc |
    Then the response status is 200
    And the response body has "content" containing 2 bookings
    And every booking in "content" has "status" equal to "PENDING"
    And the response body includes bookings "acme-pending" and "globex-pending"
    And the response body excludes booking "acme-confirmed"
    And the response body excludes booking "initech-complete"

  Scenario: List bookings filtered by customer id and status
    When a client lists bookings with query parameters:
      | customerId | 3001    |
      | status     | PENDING |
      | page       | 0       |
      | size       | 20      |
      | sort       | createdAt,desc |
    Then the response status is 200
    And the response body has "content" containing 1 booking
    And the response body includes booking "acme-pending"
    And every booking in "content" has "customerId" equal to 3001
    And every booking in "content" has "status" equal to "PENDING"

  Scenario: List response exposes stable pagination metadata
    When a client lists bookings with query parameters:
      | page | 1 |
      | size | 2 |
      | sort | createdAt,desc |
    Then the response status is 200
    And the response body has "content" containing 2 bookings
    And the response body has "page" equal to 1
    And the response body has "size" equal to 2
    And the response body has "totalElements" equal to 4
    And the response body has "totalPages" equal to 2
    And the response body has "last" equal to true
    And the response body has no pagination metadata fields other than "page", "size", "totalElements", "totalPages", and "last"

  Scenario: Empty list filters still return pagination metadata
    When a client lists bookings with query parameters:
      | customerId | 404040  |
      | status     | PENDING |
      | page       | 0       |
      | size       | 20      |
    Then the response status is 200
    And the response body has "content" equal to an empty array
    And the response body has "page" equal to 0
    And the response body has "size" equal to 20
    And the response body has "totalElements" equal to 0
    And the response body has "totalPages" equal to 0
    And the response body has "last" equal to true

  Scenario: Invalid status filter returns a structured bad request error
    When a client lists bookings with query parameters:
      | status | LOST |
    Then the response status is 400
    And the response content type is "application/json"
    And the response body has "status" equal to 400
    And the response body has "error" equal to "Bad Request"
    And the response body has "message" equal to "Parameter 'status' must be a valid BookingStatus"
    And the response body has "path" equal to "/api/v1/bookings"
    And the response body has "timestamp" as an ISO-8601 UTC timestamp
