package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import org.scalatest._
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers._
import org.apache.commons.vfs2._
import java.io._

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

  test("Direct download of image file") {
    get("/test.jpeg") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("test.jpeg")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Download file with different output format") {
    get("/params;v=0/test/content/image.gif.png") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("test/content/image.gif")
      verify(imageProcessor).transform(Matchers.eq("png"), any[InputStream], any[OutputStream], any[ImageSettings])
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
    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;img:g=n;v=0/test.epub/test/content/images/test.jpeg") {
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(
        width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop), gravity = Some(Gravity.North))
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Invalid integer parameter") {
    get("/params;v=0;img:w=16x/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*16x.*not.*valid.*value.*"))
      // Should not have tried to do anything.
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Invalid mode parameter") {
    get("/params;v=0;img:m=foo/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*foo.*not.*valid.*value.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Invalid gravity parameter") {
    get("/params;v=0;img:g=foo/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*not.*valid.*img:g.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Missing parameter") {
    get("/params;v=0;img:w=/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*invalid.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Invalid image size") {
    get("/params;v=0;img:w=2501/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*width.*2501.*"), body)
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Zero value for image size") {
    get("/params;v=0;img:w=0/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*width.*0.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

  test("Give invalid image quality setting") {
    get("/params;v=0;img:q=0/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*quality.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
    get("/params;v=0;img:q=101/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*quality.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor)
    }
  }

}
