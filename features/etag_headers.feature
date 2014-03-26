Feature: The resource server response includes an ETag header
  As an Android Client
  I want the resource server's responses to include ETag headers
  So that I can avoid a shitty android downloader bug.
  # http://papaya-backend.net/2013/04/12/why-http-etag-header-may-cause-your-downloading-apps-on-android-failed/

  Scenario: Direct downloads have ETag headers
    Given a valid ePub exists on the resource server
    When I request a direct download of that ePub
    Then the "ETag" header is present

  Scenario: Processed downloads have ETag headers
    Given a valid sample ePub exists on the resource server
    When I request a processed download of that sample ePub
    Then the "ETag" header is present

  Scenario: Files from within ePubs have ETag headers
    Given a valid sample ePub exists on the resource server
    When I request a file from within that sample ePub
    Then the "ETag" header is present