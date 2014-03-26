Feature: Access files from within ePubs
  As the developer of the online-reader app
  I want to be able to access files from within ePubs
  So that I can read an ePub sample without having to download it all beforehand

  Scenario: I can access an ePub's container XML directly
    Given a valid sample ePub exists on the resource server
    When I request the container xml from within that sample ePub
    Then the response is that container xml

  Scenario Outline: I can access images from within an ePub
    Given a valid sample ePub with images exists on the resource server
    When I request a <image type> image from within that sample ePub
    Then the response is that image
    And it has the content type "<content type>"

    Examples: Supported image types
      | image type | content type |
      | JPG        | image/jpeg   |
      | JPEG       | image/jpeg   |
      | GIF        | image/gif    |
      | PNG        | image/png    |

  Scenario: I can request resized images from within an ePub
    Given a valid sample ePub with images exists on the resource server
    When I request an image from within that sample ePub, scaled down
    Then the response is that image, at the requested size
    And it has the correct content type

  Scenario Outline: Correct content types
    Given a valid sample ePub with a range of files exists on the resource server
    When I request a <file type> file from within that sample ePub
    Then the response is that file
    And it has the content type "<content type>"

    Examples: Files with important content types
      | file type | content type           |
      | CSS       | text/css;charset=utf-8 |

  @negative
  Scenario: Files that do not exist in ePubs
    Given a valid sample ePub exists on the resource server
    When I request a file from within that sample ePub which does not exist
    Then the request fails because the file is not found
  
  Scenario: Files from within ePubs are cacheable
    Given a valid sample ePub exists on the resource server
    When I request a file from within that sample ePub
    Then the response is publicly cacheable