Feature: Key files are never downloadable
  Key files should never be downloadable directly from the resource server
  They can only be downloaded from Clortho (which will ensure you have the rights to download the keyfile)

  Scenario: Direct download for book keys are ignored
    Given a valid book key exists on the resource server
    When I request a direct download of that book key
    Then the request fails beacuse the file is not found

  Scenario: Processed download for book keys are ignored
    Given a valid book key exists on the resource server
    When I request a processed download of that book key
    Then the request fails beacuse the file is not found