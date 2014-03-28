package com.blinkboxbooks.resourceserver

import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import org.scalatra.test.scalatest.ScalatraSuite
import java.net.URLEncoder
import org.scalatra.util.RicherString._

@RunWith(classOf[JUnitRunner])
class ResourceServletFunctionalTest extends ScalatraSuite
  with FunSuite with BeforeAndAfter with MockitoSugar with ImageChecks {

  val KEY_FILE = "secret.key"
  val TOP_LEVEL_FILE = "toplevel.html"
  val FileTypes = List("png", "gif", "jpeg")

  val imageProcessor: ImageProcessor = new ThreadPoolImageProcessor(1)
  var parentDir: File = _
  var rootDir: File = _

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

    FileUtils.write(new File(parentDir, TOP_LEVEL_FILE), "Should not be accessible")
    FileUtils.write(new File(rootDir, KEY_FILE), "Don't serve this up")
    FileUtils.write(new File(rootDir, "ch01.html"), "<p>It was a dark and stormy night...</p>")
    FileUtils.write(new File(subdir, "ch02.html"), "<p>and the wind was blowing a gale.</p>")
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.gif"), new File(rootDir, "test.gif"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.jpeg"), new File(rootDir, "test.jpeg"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.png"), new File(rootDir, "test.png"))
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.epub"), new File(rootDir, "test.epub"))
  }

  before {
    val fs = FileSystem.createZipFileSystem(rootDir.toPath(), None)
    // Mount the servlet under test.
    addServlet(ResourceServlet(fs, 0 millis, 100 millis, 250 millis, 1), "/*")
  }

  override def afterAll() {
    FileUtils.deleteDirectory(parentDir)
  }

  test("Standard headers") {
    get("sub/ch02.html") {
      assert(status === 200)
      checkIsCacheable()
      assert(header("Content-Location") === ("/sub/ch02.html"))
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

  test("Download image with image settings") {
    for (fileType <- FileTypes) {
      get(s"/params;img:w=160;v=0/test.epub/images/test.$fileType") {
        assert(status === 200)
        checkImage(response.inputStream, fileType, 160, 100)
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
    get("/" + KEY_FILE) {
      assert(status === 404)
    }
  }

  test("Try to get key file from inside an epub") {
    get("/params;v=0/test.epub/test/" + KEY_FILE) {
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
    get("/../" + TOP_LEVEL_FILE) {
      assert(status === 400)
    }
  }

  test("Specify image quality setting for PNG file") {
    get(s"/params;img:w=160;img:q=85;v=0/test.epub/images/test.png") {
      // Should just ignore the quality setting.
      assert(status === 200)
      checkImage(response.inputStream, "png", 160, 100)
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

  private def checkContentMatches(testFile: String) {
    assert(IOUtils.contentEquals(response.inputStream, getClass.getResourceAsStream(testFile)),
      s"Content of response should match file '$testFile'")
  }

  private def checkIsCacheable() {
    assert(header("Cache-Control").matches(".*max-age=\\d+.*"))
  }

}
