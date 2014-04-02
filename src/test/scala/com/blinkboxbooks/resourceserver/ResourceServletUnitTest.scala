package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import org.scalatest._
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.ArgumentCaptor
import org.apache.commons.vfs2._
import java.io._
import TestUtils._

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
  var imageCache: ImageCache = _

  before {
    // Set up mocks.
    imageProcessor = mock[ImageProcessor]
    fileSystemManager = mock[FileSystemManager]
    file = mockFile("Test")
    doReturn(file).when(fileSystemManager).resolveFile(anyString)

    imageCache = mock[ImageCache]
    doReturn(None).when(imageCache).getImage(anyString, anyInt)
    doReturn(true).when(imageCache).wouldCacheImage(any[Option[Int]])

    // Mount the servlet under test.
    addServlet(new ResourceServlet(fileSystemManager, imageProcessor, imageCache, directExecutionContext), "/*")
  }

  test("Direct download of image file") {
    get("/test.jpeg") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("test.jpeg")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Download file with different output format") {
    get("/params;v=0/test/content/image.gif.png") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("test/content/image.gif")
      verify(imageProcessor).transform(Matchers.eq("png"), any[InputStream], any[OutputStream], any[ImageSettings])
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Download file in epub file") {
    get("/params;v=0/test.epub/test/content/intro.html") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/intro.html")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Download image in epub file without image settings") {
    get("/params;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/images/test.jpeg")
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Download image with all available image settings, image not in cache") {
    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;img:g=n;v=0/test.epub/test/content/images/test.jpeg") {
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(
        width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop), gravity = Some(Gravity.North))
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      val sizeArg = ArgumentCaptor.forClass(classOf[Option[Int]])
      verify(imageCache).wouldCacheImage(sizeArg.capture())
      val fileArg = ArgumentCaptor.forClass(classOf[FileObject])
      verify(imageCache).addImage(Matchers.eq("test.epub/test/content/images/test.jpeg"), fileArg.capture())
      assert(fileArg.getValue() eq file, "Should pass on the same file to the cache that it got from the file system, got: " + fileArg.getValue() + " vs. " + file)
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Download image with all available image settings, image in cache") {
    // Set up cache to returned cached image.
    val cachedFile = mockFile("Cached file")
    doReturn(Some(cachedFile)).when(imageCache).getImage(anyString, anyInt)

    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;img:g=n;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      val imageSettings = new ImageSettings(
        width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop), gravity = Some(Gravity.North))
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      // Should not have gone to the file system manager here, as the file was cached.
      // Shouldn't have added anything new to the image cache either.
      // But should still resize the image.
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Resizing image when requested size is bigger than any cached version") {
    // Set up cache so it says image is too big.
    doReturn(false).when(imageCache).wouldCacheImage(any[Option[Int]])

    get("/params;img:w=160;v=0/test.epub/test/content/images/test.jpeg") {
      // Should get image from file, as it's not in cache.
      verify(fileSystemManager).resolveFile("zip:test.epub!/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(width = Some(160), gravity = None)
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      verify(imageCache).wouldCacheImage(Some(160))
      // Should not have added the image to the cache despite it not being there, due to the requested size.
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Invalid integer parameter") {
    get("/params;v=0;img:w=16x/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*16x.*not.*valid.*value.*"))
      // Should not have tried to do anything.
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Invalid mode parameter") {
    get("/params;v=0;img:m=foo/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*foo.*not.*valid.*value.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Invalid gravity parameter") {
    get("/params;v=0;img:g=foo/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*not.*valid.*img:g.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Missing parameter") {
    get("/params;v=0;img:w=/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*invalid.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Invalid image size") {
    get("/params;v=0;img:w=2501/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*width.*2501.*"), body)
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Zero value for image size") {
    get("/params;v=0;img:w=0/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*width.*0.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

  test("Give invalid image quality setting") {
    get("/params;v=0;img:q=0/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*quality.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
    get("/params;v=0;img:q=101/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*quality.*"))
      verifyNoMoreInteractions(fileSystemManager, imageProcessor, imageCache)
    }
  }

}
