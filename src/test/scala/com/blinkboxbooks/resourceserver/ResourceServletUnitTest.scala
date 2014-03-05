package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatest.mock.MockitoSugar
import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers._
import org.apache.commons.vfs2._
import java.io._
import org.scalatest.prop.Checkers

/**
 * Unit tests for resource servlet.
 * These typically check the exact arguments that get passed on to related
 * objects such as the image processor, as opposed to the results coming back
 * from a real image processor.
 */
@RunWith(classOf[JUnitRunner])
class ResourceServletUnitTest extends ScalatraSuite
  with FunSuite with BeforeAndAfter with MockitoSugar {

  import ResourceServlet._

  var imageProcessor: ImageProcessor = _
  var fileSystemManager: FileSystemManager = _
  var file: FileObject = _
  var content: FileContent = _
  var testInputStream: InputStream = _

  before {
    // Set up mocks.
    imageProcessor = mock[ImageProcessor]
    fileSystemManager = mock[FileSystemManager]
    var file = mock[FileObject]
    var content = mock[FileContent]
    var testInputStream = new ByteArrayInputStream("Test".getBytes)
    doReturn(file).when(fileSystemManager).resolveFile(anyString)
    doReturn(true).when(file).exists()
    doReturn(FileType.FILE).when(file).getType()
    doReturn(content).when(file).getContent()
    doReturn(testInputStream).when(content).getInputStream()

    // Mount the servlet under test.
    addServlet(new ResourceServlet(fileSystemManager, imageProcessor), "/*")
  }

  // Keep these - or just spy on the real image processor in the functional tests instead?
  test("Direct download of image file") {
    get("/test.jpeg") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("test.jpeg")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Download file in epub file") {
    get("/params;v=0/test.epub/test/content/intro.html") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/intro.html")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Download image in epub file without image settings") {
    get("/params;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/images/test.jpeg")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Download image with all available image settings") {
    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop))
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  ignore("Invalid image size") {
    get("/todo") {
      fail("TODO")
    }
  }

  ignore("Give invalid image quality setting") {
    get("/todo") {
      fail("TODO")
    }
  }

  test("get VFS path") {
    // Simple paths.
    val simpleFile = "foo/bar/stuff.xml"
    assert(getVfsPath(simpleFile) === simpleFile)
    val epubFile = "/foo/bar/stuff.epub"
    assert(getVfsPath(epubFile) === epubFile)
    val topLevelEpubFile = "stuff.epub"
    assert(getVfsPath(topLevelEpubFile) === topLevelEpubFile)

    // Container paths.
    assert(getVfsPath("foo/bar/stuff.epub/stuff.xml") === "zip:foo/bar/stuff.epub!/stuff.xml")
    assert(getVfsPath("foo/bar/stuff.epub/dir/stuff.xml") === "zip:foo/bar/stuff.epub!/dir/stuff.xml")
    assert(getVfsPath("foo/bar/stuff.EPUB/dir/stuff.xml") === "zip:foo/bar/stuff.EPUB!/dir/stuff.xml")
    assert(getVfsPath("foo/bar/stuff.zip/stuff.xml") === "zip:foo/bar/stuff.zip!/stuff.xml")
    assert(getVfsPath("foo/bar/stuff.ZIP/stuff.xml") === "zip:foo/bar/stuff.ZIP!/stuff.xml")

    // Containers in containers.
    assert(getVfsPath("foo/bar/stuff.zip/dir/file.zip") === "zip:foo/bar/stuff.zip!/dir/file.zip")
    assert(getVfsPath("foo/bar/stuff.zip/dir/file.zip/inner.xml") === "zip:foo/bar/stuff.zip!/dir/file.zip!/inner.xml")
    assert(getVfsPath("foo/bar/stuff.zip/dir/file.epub/inner.xml") === "zip:foo/bar/stuff.zip!/dir/file.epub!/inner.xml")

    // Unsupported container extension.
    val gzipPath = "foo/bar/stuff.gz/stuff.xml"
    assert(getVfsPath(gzipPath) === gzipPath)
  }

  test("get extension") {
    assert(fileExtension("foo.t") === Some("t"))
    assert(fileExtension("f.t") === Some("t"))
    assert(fileExtension("foo.html") === Some("html"))
    assert(fileExtension("foo.Html") === Some("html"))
    assert(fileExtension("foo.HTML") === Some("html"))
    assert(fileExtension("foo.txt") === Some("txt"))
    assert(fileExtension("foo.bar.txt") === Some("txt"))
    assert(fileExtension("") === None)
    assert(fileExtension("f") === None)
    assert(fileExtension("foo") === None)
    assert(fileExtension("foo-bar") === None)
  }

}
