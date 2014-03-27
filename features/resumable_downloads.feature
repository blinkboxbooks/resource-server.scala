Feature: Resumable downloads
  As the developer of the client apps
  I want to be able to download specific sections of a file
  So that I can resume a download of a book

  Scenario: Direct downloads of the end of an ePub
    Given a valid ePub exists on the resource server
    When I request a direct download of that ePub from a specific byte location onwards
    Then I receive the correct bytes of that ePub

  Scenario: Processed downloads of the end of an ePub
    Given a valid ePub exists on the resource server
    When I request a processed download of that ePub from a specific byte location onwards
    Then I receive the correct bytes of that ePub