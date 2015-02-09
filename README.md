# Resource Server

This is a service that serves up ePub files and related content, including dynamically
sized images. This version is a Scala implementation that's API compatible with the original Ruby version.

The service builds as a fat jar that can be deployed stand-alone, and only needs a JDK (version 7+) to run.

## Features

This services provides:

- General file access.
- Access to files inside ePub files, e.g. to enable reading of book samples in web clients.
- Server-side dynamic resizing and transformation of images, avoiding expensive image processing on low powered clients and minimising bandwidth usage.


## API conventions

### Cacheing

Files provided by the resourve server are considered immutable, hence may and should be extensively cached. The service will set the `Cache-Control` and `Expires` headers in responses to indicate an expiry time of 1 year.

### Matrix parameters

To provide parameters for resizing and transforming images, we could have used query parameters in requests to specify image parameters. However, we have found in the past that some upstream caches do not take query parameters into account in the case of image files (.jpg and .png). I.e. they may treat URLs that refer to image files the same, even if they have different query parameters.

To avoid this problem, we specify image parameters as part of the resource path in URLs, specifically using "matrix parameters" as originally described in [Matrix URIs](http://www.w3.org/DesignIssues/MatrixURIs.html) by Tim Berners-Lee.

### API versioning

For similar arguments as above, we specify the API version as part of the URL. The only supported version so far is v=0.

### Content-Location

Every request will have a `Content-Location` header which specifies the canonical request URL for what was requested. This means requesting `/params;img:m=scale!;img:w=200/` and `/params;img:w=200/` will have the same `Content-Location` (as m=scale! is the default) so they can be recognised as the same resource, and consecutive requests for these different URLs can be pulled from the same cache.

### Range header support

Some ePub files are very lagre, and they may be downloaded to mobile clients across low bandwidth links. Hence it's important that clients are able to resume failed downloads. We achieve this by using the HTTP `Range` header. This resource server doesn't support the full syntax of this header, but handles a *single* range only, specifying ranges as bytes, and prefix ranges but not suffix ranges. 

For example, to resume downloading a file after 10,000 bytes, use a `Range` header of `bytes=10000-`. (Note the trailing hyphen that specifies an open range)

## API

### Basic file retrieval

Files can be downloaded directly, by using URLs like:

`http://resource.server/path/to/file`

or

`http://resource.server/params;v=0/path/to/file`


### On the fly image transformation

When accessing image files, the service can transform the returned image by:

- Transcoding to a different file format.
- Resizing.
- Changing aspect ratio by stretching, cropping or padding.
- Change image quality settings, e.g. compression level.

Image format is changed simply by appending a file extension to the requested image. For example:

`http://resource.server/params;v=0/image.png.jpg`

will return the `image.png` image transcoded to JPEG.

All other image transform parameters are given as part of the matrix parameters in the URL. For example, this URL:

`http://resource.server/params;v=0;img:h=100;img:w=150/image.png`

will return the `image.png` file with a height of 100 pixels and a width of 150.

The full set of supported parameters are given in this table:

Parameter    | Name          | Format       | Description
------------ | ------------- | ------------ | -----------------
img:w        | Width         | Integer      | Width of image in pixels
img:h        | Height        | Integer      | Height of image in pixels
img:m        | Mode          | One of: `scale`, `scale!`, `crop`, `stretch` | How image is fit to requested size if the required aspect ratio is different from the original. `scale!` will perform upscaling if the requested size is bigger than the original, `scale` will only perform downscaling.
img:q        | Quality       | Decimal (0-100) | Relative quality of requested image, 100 being the best quality available. Exact meaning depends on requested format. |

The resizing algorithm currently use is a **Lanzcos Filter**. This algorithm is slow but provides very high quality results, especially when producing thumbnails where it's important that text remains legible - very important for book covers for example!

Earlier versions used a much faster but lower quality algorithm provided by the [ImageScalr library](http://www.thebuzzmedia.com/software/imgscalr-java-image-scaling-library). The speed difference is very large, e.g. 3 ms vs 100 ms, so for some applications it could be worth changing this, or making the algorithm configurable.

### Accessing files inside ePub files

The Blinkbox Books web app implements a reader for ePub samples that lets users skim through books as they're browsing the shop. ePub files can be very large though, and clients clearly don't want to download a large ePub file just to show a few pages of it in a preview.

Thus we implemented an API that lets clients access individual resources inside ePub files, for example HTML files and images. The syntax for this is to specify the path of the epub file, then an exclamation mark `!` as a separator, followed by the path of the file inside the epub. For example, this URL:

`http://resource.server/path/to/book.epub!path/to/image.png`

will access the file `image.png` in the given directory inside the `book.epub` file.

## Local cacheing of image files

This version of the Resource Service adds experimental support for cacheing versions of image files based on a predefined set of image sizes. The rationale for this is that performance tests show that when dealing with very large original images, most of the time spent in requests is in reading the originals, not performing image transformations. In particular, when serving small versions of files for thumbnails etc. it's very inefficient to read large originals of several megapixels. 

Instead, the service can be optionally configured to cache a set of smaller versions of the originals and store these in a file system along with the originals. When getting later requests for scaled-down images, it will then use the smallest version of the image that's equal to or bigger than the requested image.

Tests show that this can reduce request times, for example from ~120 ms to <20 ms when dealing with large originals, as well as reducing the I/O load on the server significantly.

## Build and run

The resource server builds as a standalone Jar file using `sbt`.

It uses the common Blinkbox Books conventions and approaches to configuration, metrics, health endpoints etc., see [the common-config library](/blinkboxbooks/common-config.scala) for details.

See the [application.conf](/src/main/resources/application.conf) file for properties that need to be provided, and [reference.conf](/src/main/resources/reference.conf) for settings that can optionally be overridden.

## Running Cucumber tests

The Resource Server comes with a comprehensive set of functional tests, specified and run using Cucumber.

To run these tests, you must insure you have `libpng` and `imagemagick` installed, e.g. on OS/X you could install them using:

```
$ brew install libpng imagemagick
```

Then you can follow the usual bundle install and cucumber procedure, by running these in the root folder of the Resource Server project:

```
$ bundle install
$ MOUNT_DIR=<image directory> bundle exec cucumber
```

**NOTE:** The image directory given to the Cucumber tests *must* match the corresponding setting in the `application.conf` file used with the service.

## Running Gatling tests

The service has some performance tests written using [Gatling](http://gatling.io). See [gatling.md](/performance-tests/images/gatling.md) for details on how these work and how to run them.
