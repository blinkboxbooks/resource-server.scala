Feature: Image resizing
  As the developer of the shop website
  I want to be able to retrieve images at a specific size
  So that I can optimise download speed for slower connections

  Scenario: Scaled

  Scenario: Stretched

  Scenario Outline: Images can be resized while cropping, with gravity
    Given an image with the following attributes exists on the resource server:
      | Height | 2000 |
      | Width  | 1000 |
    When I request that image with the following filters:
      | Image: Mode   | Crop |
      | Image: Height | 500 |
      | Image: Width  | 500 |
    Then the response is an image
    And it has the following attributes:
      | Height | 1000 |
      | Width  | 500  |

    Examples: 

  Scenario Outline: Images can be requested in multiple formats
    Given an image exists on the resource server
    When I request that image in <format> format
    Then the response is an image
    And it has the following attributes:
      | Format | <format> |

    Examples: Supported image formats
      | format |
      | jpg    |
      | png    |

  Scenario: Upscaled 'scale' is the default resize type
    Given an image with the following attributes exists on the resource server:
      | Height | 200 |
      | Width  | 100 |
    When I request that image with the following filters:
      | Image: Height | 1000 |
      | Image: Width  | 500  |
    Then the response is an image
    And it has the following attributes:
      | Height | 1000 |
      | Width  | 500  |

  Scenario Outline: Content location header for cacheability
    The content location header should reflect the canonical and explicit URL for the resized image so that two requests that result in the same file can be cached intelligently by the CDN and the customer's browsers.

    Given an image with the following attributes exists on the resource server:
      | Height | 2000 |
      | Width  | 1000 |
    When I request that image with the following filters:
      | Image: Mode   | Scale   |
      | Image: Height | 1000    |
      | Image: Width  | <width> |
    Then the response is an image
    And the "Content-Location" header references this image with the following filters:
      | Image: Mode   | Scale |
      | Image: Height | 1000  |
      | Image: Width  | 500   |

    Examples: Widths 500 px or bigger that will make no difference to the output image in 'scale' mode
      | width |
      | 500   |
      | 550   |
      | 1000  |

  Scenario: JPEG Image quality
    Given a high quality image exists on the resource server
    When I request that image with the following filters:
      | Image: Format  | JPG |
      | Image: Quality | 50  |
    Then the response is an image
    And it is more compressed than the original


  Scenario Outline: zero-width or -height resizes are invalid
    Given an image exists on the resource server
    When I request that image with the following filters:
      | <filter name> | <filter value> |
    Then the request fails because it is invalid

    Examples: Invalid filter parameters
      | filter name   | filter value |
      | Image: Width  | 0            |
      | Image: Height | 0            |
