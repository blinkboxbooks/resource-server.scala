Feature: The resource server supports CORS requests
  As a developer of the front end
  I want the resource server to respond with CORS headers
  So that I can query the resource server directly from the website, rather than having to write code to proxy it through my subdomain

  Scenario: Direct downloads have CORS headers
    Given a valid ePub exists on the resource server
    When I request a direct download of that ePub
    Then the "Access-Control-Allow-Origin" header is set to "*"

  Scenario: Processed downloads have CORS headers
    Given a valid sample ePub exists on the resource server
    When I request a processed download of that ePub
    Then the "Access-Control-Allow-Origin" header is set to "*"

  Scenario: Files from within ePubs have CORS headers
    Given a valid sample ePub exists on the resource server
    When I request a file from within that sample ePub
    Then the "Access-Control-Allow-Origin" header is set to "*"
