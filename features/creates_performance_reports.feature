Feature: Creates performance reports
  As a developer
  I want to know how fast the server is responding to requests
  So that I can optimise calls which are being slow, so users have a fast browsing experience

  Scenario: Requesting an image creates performance details in the performance log
    Given an image exists on the resource server
    When I request a direct download of that image
    Then there is a new line in the performance log