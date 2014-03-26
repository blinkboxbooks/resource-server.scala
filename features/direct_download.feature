Feature: Download stored files
  As the developer of the customer-facing media caches
  I want to be able to retrieve files stored on the internal media server
  So that I can send them to the customer

  Scenario: Downloading an ePub directly
    Given a valid ePub exists on the resource server
    When I request a direct download of that ePub
    Then the response is that ePub
    And it is publicly cacheable

  Scenario: Downloading a sample ePub directly
    Given a valid sample ePub exists on the resource server
    When I request a direct download of that sample ePub
    Then the response is that sample ePub
    And it is publicly cacheable