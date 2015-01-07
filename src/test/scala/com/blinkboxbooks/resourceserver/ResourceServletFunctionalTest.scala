package com.blinkboxbooks.resourceserver

import java.io.File
import java.nio.file.Files
import org.eclipse.jetty.servlet.{ServletHolder, ServletMapping}

import scala.concurrent.duration._
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuiteLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatra.util.RicherString._
import TestUtils._

@RunWith(classOf[JUnitRunner])
class ResourceServletFunctionalTest extends ScalatraSuite
  with FunSuiteLike with BeforeAndAfter with MockitoSugar with ImageChecks {

  val KeyFile = "secret.key"
  val TopLevelFile = "toplevel.html"
  val FileTypes = List("png", "gif", "jpeg")

  val imageProcessor: ImageProcessor = new ThreadPoolImageProcessor(1)
  var imageCache: ImageCache = _
  var parentDir: File = _
  var rootDir: File = _
  var resolver: FileResolver = _

  override def beforeAll() {
    super.beforeAll()

    // Set up a temporary directory with files for test.
    val topLevel = Files.createTempDirectory(getClass.getName)
    parentDir = new File(topLevel.toFile, "root")
    parentDir.mkdir()
    rootDir = new File(parentDir, "content")
    rootDir.mkdir()
    val subdir = new File(rootDir, "sub")
    subdir.mkdir()

    resolver = new EpubEnabledFileResolver(rootDir.toPath)

    val cacheDir = topLevel.resolve("file-cache")
    imageCache = new FileSystemImageCache(cacheDir, Set(400, 900), resolver, writingEnabled = true)

    FileUtils.write(new File(parentDir, TopLevelFile), "Should not be accessible")
    FileUtils.write(new File(rootDir, KeyFile), "Don't serve this up")
    FileUtils.write(new File(rootDir, "ch01.html"), "<p>It was a dark and stormy night...</p>")
    FileUtils.write(new File(subdir, "ch02.html"), "<p>and the wind was blowing a gale.</p>")
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.gif"), new File(rootDir, "test.gif"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.jpeg"), new File(rootDir, "test.jpeg"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.png"), new File(rootDir, "test.png"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.epub"), new File(rootDir, "test.epub"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/spaces.epub"), new File(rootDir, "spaces.epub"))
  }

  before {
    // Mount the servlet under test.
    addServlet(ResourceServlet(resolver, imageCache, directExecutionContext, 1, 0 millis, 100 millis, 250 millis), "/*")
  }

  after {
    // Unmount the servlets.
    servletContextHandler.getServletHandler.setServletMappings(new Array[ServletMapping](0))
    servletContextHandler.getServletHandler.setServlets(new Array[ServletHolder](0))
  }

  override def afterAll() {
    FileUtils.deleteDirectory(parentDir)
  }

  test("Standard headers") {
    get("sub/ch02.html") {
      assert(status === 200)
      checkIsCacheable()
      assert(header("Content-Location") === "/sub/ch02.html")
      val expectedExpiryTime = 365 * 24 * 60 * 60
      assert(header("Cache-Control") === s"public, max-age=$expectedExpiryTime")
      assert(header("X-Application-Version").matches("""\d+\.\d+\.\d+"""))
      assert(header("Access-Control-Allow-Origin") === "*")
      assert(!header("Date").isEmpty())
    }
  }

  test("Direct file access") {
    get("/ch01.html") {
      assert(status === 200)
      assert(body.contains("It was a dark and stormy night..."))
      checkIsCacheable()
    }
  }

  test("Direct file access in subdirectory") {
    get("/sub/ch02.html") {
      assert(status === 200)
    }
  }

  test("Direct download of epub file") {
    get("/test.epub") {
      assert(status === 200)
      checkContentMatches("/test.epub")
      checkIsCacheable()
    }
  }

  test("Download file inside epub file") {
    get("/params;v=0/test.epub/content/intro.html") {
      assert(status === 200)
      assert(body.contains("Welcome"))
      assert(header("Content-Length") === "28")
      assert(header("Content-Type") === "text/html")
      checkIsCacheable()
    }
  }

  test("Download file inside epub file with spaces in path") {
    get("/params;v=0/spaces.epub/OPS/001%20-%20Cover.xhtml") {
      assert(status === 200)
      assert(body.contains("Cover"))
      assert(header("Content-Length") === "810")
      assert(header("Content-Type") === "application/xhtml+xml")
      checkIsCacheable()
    }
  }

  test("Download file with parameter when using path separators at start of path") {
    for (slashes <- List("", "/", "/////")) {
      get(s"/params;v=0/${slashes}test.epub/content/intro.html") {
        assert(status === 200)
        assert(body.contains("Welcome"))
        assert(header("Content-Length") === "28")
        assert(header("Content-Type") === "text/html")
        checkIsCacheable()
      }
    }
  }

  test("Download file with parameter when using path separators after epub file name") {
    for (slashes <- List("", "/", "/////")) {
      get(s"/params;v=0/test.epub/${slashes}content/intro.html") {
        assert(status === 200)
        assert(body.contains("Welcome"))
        assert(header("Content-Length") === "28")
        assert(header("Content-Type") === "text/html")
        checkIsCacheable()
      }
    }
  }

  test("Download non-image file inside epub file, with image params") {
    get("/params;img:w=100;v=0/test.epub/content/intro.html") {
      assert(status === 200)
      assert(body.contains("Welcome"))
      assert(header("Content-Length") === "28")
      assert(header("Content-Type") === "text/html")
      checkIsCacheable()
    }
  }

  test("Download image inside epub file") {
    get("/params;v=0/test.epub/images/test.jpeg") {
      assert(status === 200)
      checkImage(response.inputStream, "jpeg", 320, 200)
      assert(header("Content-Length") === "22024")
      assert(header("Content-Type") === "image/jpeg")
      checkIsCacheable()
    }
  }

  test("Download image without image settings") {
    for (fileType <- FileTypes) {
      get(s"/params;v=0/test.epub/images/test.$fileType") {
        assert(status === 200)
        checkImage(response.inputStream, fileType, 320, 200)
        assert(header("Content-Type") === s"image/$fileType")
      }
    }
  }

  // CP-1701
  test("ETag header value is surrounded by quote marks") {
    get("/params;v=0/test.epub/images/test.jpeg") {
      assert(status === 200)
      val etag = header("ETag")
      assert(etag.toString.matches("\"(.+)\""))
    }
  }

  test("Download image with image settings") {
    for (fileType <- FileTypes) {
      get(s"/params;img:w=160;v=0/test.epub/images/test.$fileType") {
        assert(status === 200)
        checkImage(response.inputStream, fileType, 160, 100)
      }
    }
  }

  test("Repeatedly download resized image") {
    // Repeatedly request files of different size that should hit the same cached image.
    for (fileType <- FileTypes) {
      for (size <- Seq(160, 100, 200)) {
        get(s"/params;img:w=$size;v=0/test.epub/images/test.$fileType") {
          assert(status === 200)
          checkImage(response.inputStream, fileType, size)
        }
      }
    }
  }

  test("Transcode image") {
    for (sourceFileType <- FileTypes) {
      for (targetFileType <- FileTypes) {
        get(s"/params;v=0/test.epub/images/test.$sourceFileType.$targetFileType") {
          assert(status === 200)
          checkImage(response.inputStream, targetFileType, 320, 200)
          assert(header("Content-Type") === s"image/$targetFileType")
        }
      }
    }
  }

  test("Try to get file that doesn't exist") {
    get("/foo.txt") {
      assert(status === 404)
    }
  }

  val encodedFilename = KeyFile.replaceAll("e", "%65")
  val keyfilePaths = Seq(
    s"/$KeyFile",
    s"/$encodedFilename",
    s"/$KeyFile/bar/..",
    s"/$encodedFilename/bar/..",
    s"/content/../$KeyFile",
    s"/content/../$KeyFile/bar/..")

  test("Try to get key file") {
    for (path <- keyfilePaths) {
      get(path) {
        assert(status === 404, s"path=$path")
      }
    }
  }

  test("Try to get key file using params") {
    def withParameters(filename: String) = "/params;v=0" + filename
    for (path <- keyfilePaths.map(withParameters))
      get(path) {
        assert(status === 404, s"path=$path")
      }
  }

  test("Try to get file in archive that doesn't exist") {
    get("/params;v=0/unknown.epub/test/unknown.html") {
      assert(status === 404)
    }
  }

  test("Try to get file that doesn't exist from within an archive") {
    get("/params;v=0/test.epub/test/unknown.html") {
      assert(status === 404)
    }
  }

  test("Try to access parent directory of root") {
    get("/../" + TopLevelFile) {
      assert(status === 400)
    }
  }

  test("Specify image quality setting for PNG file") {
    get(s"/params;img:q=85;img:w=160;v=0/test.epub/images/test.png") {
      // Should just ignore the quality setting.
      assert(status === 200)
      checkImage(response.inputStream, "png", 160, 100)
      assert(header("Content-Location") === "/params;img:h=100;img:m=scale!;img:q=85;img:w=160;v=0/test.epub/images/test.png")
    }
  }

  test("Mode \"scale\" does not upscale images") {
    get(s"/params;img:q=85;img:w=1000;img:h=1000;img:m=scale;v=0/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 320, 200)
    }
  }

  test("Mode \"scale!\" does upscale images") {
    // CP-1789
    // should perform the largest possible transformation to fit in the bounding box
    get(s"/params;img:q=85;img:w=640;img:h=500;img:m=scale!;v=0/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 640, 400)
    }
    get(s"/params;img:q=85;img:h=1000;img:w=400;img:m=scale!;v=0/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 400, 250)
    }
  }

  test("Mode \"scale!\" is the default resize mode") {
    get(s"/params;img:q=85;img:w=640;img:h=500;v=0/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 640, 400)
    }
  }

  test("cropping image to smaller aspect ratio works") {
    get(s"/params;img:q=85;img:w=170;img:h=45;v=0;img:m=crop/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 170, 45)
    }
  }

  test("Check against cross-site scripting attack") {
    val paths = List(
      "/params;v=0;img:w=<script>alert('attacked')</script>/test.epub/images/test.jpeg",
      "/params;v=0;img:h=<script>alert('attacked')</script>/test.epub/images/test.jpeg",
      "/params;v=0;img:q=<script>alert('attacked')</script>/test.epub/images/test.jpeg",
      "/params;v=0;img:m=<script>alert('attacked')</script>/test.epub/images/test.jpeg",
      "/params;v=0;img:g=<script>alert('attacked')</script>/test.epub/images/test.jpeg")
    for (path <- paths) {
      get(path.urlEncode) {
        assert(status === 400, s"Path '$path.urlEncode' should give a bad request error")
        assert(!body.contains("<script>"), s"Error message for '$path.urlEncode' should not contain the unescaped input")
      }
    }

    get("/dodgy-path-<script>alert('attacked')</script>.gif".urlEncode) {
      assert(status === 404)
      assert(!body.contains("<script>"))
    }
  }

  test("Direct file access with Range header") {
    get("/test.png", headers = Map("Range" -> "bytes=100-")) {
      val expectedSize = IOUtils.toByteArray(getClass.getResourceAsStream("/test.png")).length - 100
      assert(status === 206 &&
        bodyBytes.size === expectedSize &&
        header.get("Content-Length") === Some(expectedSize.toString))
      checkIsCacheable()
    }
  }

  test("Direct file access with Range header request that exceeds size of file") {
    get("/test.png", headers = Map("Range" -> "bytes=9999999-")) {
      // This isn't quite right according to the RFC - it ought to return a 
      // status of 416, but this requires knowing a priory the size of the returned data.
      assert(status === 206 &&
        bodyBytes.size === 0 &&
        header.get("Content-Length") === Some(0.toString))
      checkIsCacheable()
    }
  }

  test("Direct file access with unsupported Range header") {
    for (rangeHeader <- Seq("bytes=-100", "bytes=-", "bytes=XYZ-")) {
      get("/test.png", headers = Map("Range" -> rangeHeader)) {
        // Should get full response.
        val expectedSize = IOUtils.toByteArray(getClass.getResourceAsStream("/test.png")).length
        assert(status === 200 &&
          bodyBytes.size === expectedSize &&
          header.get("Content-Length") === Some(expectedSize.toString), s"rangeHeader=$rangeHeader")
        checkIsCacheable()
      }
    }
  }

  test("Direct file access with Range header, when file can't be read.") {
    get("/unknown.png", headers = Map("Range" -> "bytes=100-")) {
      assert(status === 404)
    }
  }

  test("Direct file access with If-Range header") {
    get("/test.png", headers = Map(
      "If-Range" -> "\"ThisIsAnEtag\"",
      "Range" -> "bytes=100-")) {
      assert(status === 206)
      val expectedSize = IOUtils.toByteArray(getClass.getResourceAsStream("/test.png")).length - 100
      assert(bodyBytes.size === expectedSize)
      assert(header.get("Content-Length") === Some(expectedSize.toString))
      checkIsCacheable()
    }
  }

  test("Get file with params and Range header") {
    get("/params;v=0/test.epub/images/test.png", headers = Map("Range" -> "bytes=100-")) {
      // Should ignore range header here and return full response.
      assert(status === 200)
      val expectedSize = IOUtils.toByteArray(getClass.getResourceAsStream("/test-epub/images/test.png")).length
      assert(bodyBytes.size === expectedSize)
      assert(header.get("Content-Length") === Some(expectedSize.toString))
      checkIsCacheable()
    }
  }

  private def checkContentMatches(testFile: String) {
    assert(IOUtils.contentEquals(response.inputStream, getClass.getResourceAsStream(testFile)),
      s"Content of response should match file '$testFile'")
  }

  private def checkIsCacheable() {
    assert(header("Cache-Control").matches(".*max-age=\\d+.*"))
  }

}
