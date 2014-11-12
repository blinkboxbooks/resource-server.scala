package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatra.util.RicherString._
import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import org.scalatest._
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.ArgumentCaptor
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import scala.util.Success
import TestUtils._
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import java.util.concurrent.RejectedExecutionException

/**
 * Unit tests for resource servlet.
 * These typically check the exact arguments that get passed on to related
 * objects such as the image processor, as opposed to the results coming back
 * from a real image processor.
 */
@RunWith(classOf[JUnitRunner])
class ResourceServletUnitTest extends ScalatraSuite
  with FunSuiteLike with BeforeAndAfter with MockitoSugar {

  var imageProcessor: ImageProcessor = _
  var fileResolver: FileResolver = _
  var imageCache: ImageCache = _
  var inputStream: InputStream = _

  before {
    // Set up mocks.
    imageProcessor = mock[ImageProcessor]

    // Mock file system bits so we can check that streams are closed correctly etc.
    fileResolver = mock[FileResolver]
    inputStream = spy(new ByteArrayInputStream("Test".getBytes("UTF-8")))
    doReturn(Success(inputStream)).when(fileResolver).resolve(anyString)

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
      verify(inputStream, atLeastOnce).close()
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
      // Why does a Scala API return null??
      assert(header(ResourceServlet.CACHE_INDICATION_HEADER) === null)
    }
  }

  test("Download file with different output format") {
    get("/params;v=0/test/content/image.gif.png") {
      assert(status === 200)
      verify(fileResolver).resolve("test/content/image.gif")
      verify(imageProcessor).transform(Matchers.eq("png"), any[InputStream], any[OutputStream],
        any[ImageSettings], any[Option[ImageSettings => Unit]])
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download file in epub file") {
    get("/params;v=0/test.epub/test/content/intro.html") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/intro.html")
      verify(inputStream, atLeastOnce).close()
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

  test("Download image in epub with encoded URL") {
    get("/params%3Bv%3D0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download image in epub with lower-case encoded URL") {
    get("/params%3bv%3d0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download image when using double slash at start of path") {
    get("/params;v=0//test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download image with all available image settings, image not in cache") {
    doReturn(None).when(imageCache).getImage(anyString, anyInt)

    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;img:g=n;v=0/test.epub/test/content/images/test.jpeg") {
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(
        width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop), gravity = Some(Gravity.North))
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream],
        Matchers.eq(imageSettings), any[Some[ImageSettings => Unit]])
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      val sizeArg = ArgumentCaptor.forClass(classOf[Option[Int]])
      verify(imageCache).wouldCacheImage(sizeArg.capture())
      verify(imageCache).addImage("test.epub/test/content/images/test.jpeg")

      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Download image with all available image settings, image in cache") {
    // Set up cache to returned cached image.
    val cachedInput = spy(new ByteArrayInputStream("Test".getBytes("UTF-8")))
    doReturn(Some(cachedInput)).when(imageCache).getImage(anyString, anyInt)

    get("/params;img:w=160;img:h=120;img:q=42;img:m=crop;img:g=n;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      assert(header(ResourceServlet.CACHE_INDICATION_HEADER) === "true")
      val imageSettings = new ImageSettings(
        width = Some(160), height = Some(120), quality = Some(0.42f), mode = Some(Crop), gravity = Some(Gravity.North))
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      // Should not have gone to the file system manager here, as the file was cached.
      // Shouldn't have added anything new to the image cache either.
      // But should still resize the image.
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream],
        Matchers.eq(imageSettings), any[Some[ImageSettings => Unit]])
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Resizing image with floating point width/height parameters ignores the fractional part") {
    // We really don't want to allow this but it turns out the old ruby resource server did allow floating point
    // width and height and Tesco have depended on this for their Hudl2 promotion cards. Obviously partial pixels
    // make no sense but there's nothing much we can do about it now.
    get("/params;img:w=160.0;img:h=120.7;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      val imageSettings = new ImageSettings(width = Some(160), height = Some(120), gravity = None)
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream],
        Matchers.eq(imageSettings), any[Some[ImageSettings => Unit]])
    }
  }

  test("Resizing image when requested size is bigger than any cached version") {
    // Set up cache so it says image is too big.
    doReturn(false).when(imageCache).wouldCacheImage(any[Option[Int]])

    get("/params;img:w=160;v=0/test.epub/test/content/images/test.jpeg") {
      // Should get image from file, as it's not in cache.
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
      val imageSettings = new ImageSettings(width = Some(160), height = Some(120), gravity = None)
      verify(imageProcessor).transform(Matchers.eq("jpeg"), any[InputStream], any[OutputStream],
        Matchers.eq(imageSettings), any[Some[ImageSettings => Unit]])
      verify(imageCache).getImage("test.epub/test/content/images/test.jpeg", 160)
      verify(imageCache).wouldCacheImage(Some(160))
      // Should not have added the image to the cache despite it not being there, due to the requested size.
      verifyNoMoreInteractions(fileResolver, imageProcessor, imageCache)
    }
  }

  test("Get uncached image when when caching queue is full") {
    // Create an executor that will refuse to accept the second job it's given.
    val executor = mock[Executor]
    val exception = new RejectedExecutionException("Test exception")
    doNothing()
      .doThrow(exception)
      .when(executor).execute(any(classOf[Runnable]))
    val limitedExecutionContext = ExecutionContext.fromExecutor(executor)
    addServlet(new ResourceServlet(fileResolver, imageProcessor, imageCache, limitedExecutionContext), "/*")

    // Make two requests and check that both succeed, even though the image in the second request couldn't be cached.
    get("/params;img:w=160;v=0/test.epub/test/content/images/test.jpeg") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/images/test.jpeg")
    }
    get("/params;img:w=160;v=0/test.epub/test/content/images/test.png") {
      assert(status === 200)
      verify(fileResolver).resolve("test.epub/test/content/images/test.png")
      verify(inputStream, atLeastOnce).close()
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

  test("Get request with no version") {
    get("/params;w=500/test/content/image.gif.png") {
      assert(status === 400)
      assert(body.toLowerCase.contains("no version"))
    }
  }

  test("Get request with non-matching version") {
    get("/params;v=1/test/content/image.gif.png") {
      assert(status === 400)
      assert(body.contains("Server version 1 is not yet specified"), s"got ${body}")
    }
  }

  test("XSS attack on server version") {
    get("/params;v=<script>alert('attacked')</script>/test/content/image.gif.png".urlEncode) {
      assert(status === 400)
      assert(body.matches(".*should be.*integer.*"))
      assert(!body.contains("<script>"))
    }
  }

}
