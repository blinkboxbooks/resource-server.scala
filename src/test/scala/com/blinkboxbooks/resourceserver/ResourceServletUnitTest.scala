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
import TestUtils._
import java.nio.file.FileSystems
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import scala.util.Success

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
  var fileResolver: FileResolver = _
  var imageCache: ImageCache = _
  var path: Path = _
  var inputStream: InputStream = _

  before {
    // Set up mocks.
    imageProcessor = mock[ImageProcessor]

    // Mock file system bits so we can check that streams are closed correctly etc.
    fileResolver = mock[FileResolver]
    path = mock[Path]
    doReturn(Success(path)).when(fileResolver).resolve(anyString)
    val fileSystem = mock[FileSystem]
    doReturn(fileSystem).when(path).getFileSystem()
    val provider = mock[FileSystemProvider]
    doReturn(provider).when(fileSystem).provider()
    inputStream = spy(new ByteArrayInputStream("Test".getBytes("UTF-8")))
    doReturn(inputStream).when(provider).newInputStream(any[Path])

    imageCache = mock[ImageCache]
    doReturn(None).when(imageCache).getImage(anyString, anyInt)
    doReturn(true).when(imageCache).wouldCacheImage(any[Option[Int]])

    // Mount the servlet under test.
    addServlet(new ResourceServlet(fileResolver, imageProcessor, imageCache, directExecutionContext), "/*")
  }

  test("Direct download of image file") {
    get("/test.jpeg") {
      assert(status === 200)
      verify(fileResolver).resolve("test.jpeg")
      verify(inputStream).close()
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download file with different output format") {
    get("/params;v=0/test/content/image.gif.png") {
      assert(status === 200)
      verify(fileResolver).resolve("test/content/image.gif")
      verify(imageProcessor).transform(Matchers.eq("png"), any[InputStream], any[OutputStream], any[ImageSettings])
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download file in epub file") {
    get("/params;v=0/test.epub/test/content/intro.html") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/intro.html")
      verify(inputStream).close()
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download image in epub file without image settings") {
    get("/params;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download image with all available image settings, image not in cache") {
    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;img:g=n;v=0/test.epub/test/content/images/test.jpeg") {
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(
        width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop), gravity = Some(Gravity.North))
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      val sizeArg = ArgumentCaptor.forClass(classOf[Option[Int]])
      verify(imageCache).wouldCacheImage(sizeArg.capture())

      val pathArg = ArgumentCaptor.forClass(classOf[Path])
      verify(imageCache).addImage(Matchers.eq("test.epub/test/content/images/test.jpeg"), pathArg.capture())
      assert(pathArg.getValue() eq path,
        "Should pass on the same path to the cache that it got from the file system, got: " + pathArg.getValue() + " vs. " + path)

      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
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
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Resizing image when requested size is bigger than any cached version") {
    // Set up cache so it says image is too big.
    doReturn(false).when(imageCache).wouldCacheImage(any[Option[Int]])

    get("/params;img:w=160;v=0/test.epub/test/content/images/test.jpeg") {
      // Should get image from file, as it's not in cache.
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(width = Some(160), gravity = None)
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream], Matchers.eq(imageSettings))
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      verify(imageCache).wouldCacheImage(Some(160))
      // Should not have added the image to the cache despite it not being there, due to the requested size.
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Invalid integer parameter") {
    get("/params;v=0;img:w=16x/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*16x.*not.*valid.*value.*"))
      // Should not have tried to do anything.
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Invalid mode parameter") {
    get("/params;v=0;img:m=foo/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*foo.*not.*valid.*value.*"))
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Invalid gravity parameter") {
    get("/params;v=0;img:g=foo/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*not.*valid.*img:g.*"))
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Missing parameter") {
    get("/params;v=0;img:w=/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*invalid.*"))
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Image size too large") {
    get("/params;v=0;img:w=2501/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*width.*2501.*"), body)
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Zero value for image size") {
    get("/params;v=0;img:w=0/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*width.*0.*"))
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Give invalid image quality setting") {
    get("/params;v=0;img:q=0/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*quality.*"))
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
    get("/params;v=0;img:q=101/test/content/image.png") {
      assert(status === 400)
      assert(body.toLowerCase.matches(".*quality.*"))
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  // TODO: MOVE SOMEWHERE COMMON?
  def mockFile(content: String): Path = {
    val path = mock[Path]
    val fileSystem = mock[FileSystem]
    doReturn(fileSystem).when(path).getFileSystem()
    val provider = mock[FileSystemProvider]
    doReturn(provider).when(fileSystem).provider()
    val input = new ByteArrayInputStream("Test".getBytes("UTF-8"))
    doReturn(input).when(provider).newInputStream(any[Path])
    path
  }

}
