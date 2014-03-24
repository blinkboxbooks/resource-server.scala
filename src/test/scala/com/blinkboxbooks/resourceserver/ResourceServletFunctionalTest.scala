package com.blinkboxbooks.resourceserver

import java.io.File
import java.nio.file.Files
import org.apache.commons.io.FileUtils
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.io.IOUtils
import javax.imageio.ImageIO

@RunWith(classOf[JUnitRunner])
class ResourceServletFunctionalTest extends ScalatraSuite
  with FunSuite with BeforeAndAfter with MockitoSugar with ImageChecks {

  val KEY_FILE = "secret.key"
  val TOP_LEVEL_FILE = "toplevel.html"

  val imageProcessor: ImageProcessor = new SynchronousScalrImageProcessor()
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
    // Mount the servlet under test.
    addServlet(ResourceServlet(rootDir.toPath()), "/*")
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
    get("/params;v=0/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 320, 200)
      assert(header("Content-Type") === "image/png")
    }
  }

  test("Download JPEG image with image settings") {
    get("/params;img:w=160;v=0/test.epub/images/test.jpeg") {
      assert(status === 200)
      checkImage(response.inputStream, "jpeg", 160, 100)
    }
  }

  test("Download GIF image with image settings") {
    get("/params;img:w=160;v=0/test.epub/images/test.gif") {
      assert(status === 200)
      assert(header("Content-Type") === "image/gif")
      checkImage(response.inputStream, "gif", 160, 100)
    }
  }

  test("Download PNG image with image settings") {
    get("/params;img:w=160;v=0/test.epub/images/test.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 160, 100)
      assert(header("Content-Type") === "image/png")
    }
  }

  test("Transform PNG image to JPEG") {
    get("/params;v=0/test.epub/images/test.png.jpeg") {
      assert(status === 200)
      checkImage(response.inputStream, "jpeg", 320, 200)
      assert(header("Content-Type") === "image/jpeg")
    }
  }

  test("Transform JPEG image to PNG") {
    get("/params;v=0/test.epub/images/test.jpeg.png") {
      assert(status === 200)
      checkImage(response.inputStream, "png", 320, 200)
      assert(header("Content-Type") === "image/png")
    }
  }

  test("Transform JPEG image to GIF") {
    get("/params;v=0/test.epub/images/test.jpeg.gif") {
      assert(status === 200)
      assert(header("Content-Type") === "image/gif")
      checkImage(response.inputStream, "gif", 320, 200)
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

  ignore("Specify image quality setting for PNG file") {
    get("/todo") {
      fail("TODO")
    }
  }

  ignore("Check against cross-site scripting attack") {
    fail("TODO")
  }

  def checkContentMatches(testFile: String) {
    assert(IOUtils.contentEquals(response.inputStream, getClass.getResourceAsStream(testFile)),
      s"Content of response should match file '$testFile'")
  }

  def checkIsCacheable() {
    assert(header("Cache-Control").matches(".*max-age=\\d+.*"))
  }

}
