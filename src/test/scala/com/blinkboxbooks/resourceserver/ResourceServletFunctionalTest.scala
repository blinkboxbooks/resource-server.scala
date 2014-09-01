package com.blinkboxbooks.resourceserver

import java.io.File
import java.nio.file.Files
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
  }

  before {
    // Mount the servlet under test.
    addServlet(ResourceServlet(resolver, imageCache, directExecutionContext, 1, 0 millis, 100 millis, 250 millis), "/*")
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
      assert(header("expires_in").toInt === expectedExpiryTime)
      assert(header("Cache-Control") === s"public, max-age=$expectedExpiryTime")
      assert(header("X-Application-Version").matches("""\d+\.\d+\.\d+"""))
      assert(header("Access-Control-Allow-Origin") === "*")
      assert(!header("now").isEmpty())
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

  test("Invalid access to absolute path") {
    get("/params;v=0//test.epub/content/intro.html") {
      assert(status === 400)
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
  test("ETag header value is surrounded by quote marks"){
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

  test("Try to get key file") {
    get("/" + KeyFile) {
      assert(status === 404)
    }
  }

  test("Try to get key file from inside an epub") {
    get("/params;v=0/test.epub/test/" + KeyFile) {
      assert(status === 404)
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
      assert(header("Content-Location") === "/params;img:h=100;img:m=scale;img:q=85;img:w=160;v=0/test.epub/images/test.png")
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
      assert(status === 200)
      val expectedSize = IOUtils.toByteArray(getClass.getResourceAsStream("/test.png")).length - 100
      assert(bodyBytes.size === expectedSize)
      assert(header.get("Content-Length") === Some(expectedSize.toString))
      checkIsCacheable()
    }
  }

  test("Get file inside epub file, with Range header") {
    get("/params;v=0/test.epub/images/test.png", headers = Map("Range" -> "bytes=100-")) {
      assert(status === 200)
      val expectedSize = IOUtils.toByteArray(getClass.getResourceAsStream("/test-epub/images/test.png")).length - 100
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
