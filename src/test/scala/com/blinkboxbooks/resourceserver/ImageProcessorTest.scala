package com.blinkboxbooks.resourceserver

import java.io._
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Matchers.any

object ImageProcessorTest {
  // Fixed test data.
  val jpegData = IOUtils.toByteArray(getClass.getResourceAsStream("/test.jpeg"))
  val pngData = IOUtils.toByteArray(getClass.getResourceAsStream("/test.png"))
}

@RunWith(classOf[JUnitRunner])
class ImageProcessorTest extends FunSuite with BeforeAndAfter with ImageChecks {

  import ImageProcessorTest._

  val processor: ImageProcessor = new ThreadPoolImageProcessor(1)
  var output: ByteArrayOutputStream = _

  def jpegImage = new ByteArrayInputStream(jpegData)
  def pngImage = new ByteArrayInputStream(pngData)
  def data(output: ByteArrayOutputStream) = new ByteArrayInputStream(output.toByteArray())

  before {
    output = new ByteArrayOutputStream()
  }

  test("No image settings given") {
    // Allow this, e.g. for trans-coding.
    processor.transform("jpeg", jpegImage, output, new ImageSettings())
    assert(output.size > 0)
    checkImage(data(output), "jpeg", 320, 200)
  }

  test("Unknown image format") {
    intercept[Exception](processor.transform("invalid", jpegImage, new ByteArrayOutputStream(), new ImageSettings()))
  }

  test("Transform png") {
    processor.transform("png", pngImage, output, new ImageSettings(Some(50), Some(40), Some(Scale), None))
    assert(output.size > 0)
    // When scaling, the image ratio is retained, hence the requested height isn't taken into account.
    checkImage(data(output), "png", 50, 31)
  }

  test("Try to specify quality setting for png file") {
    processor.transform("png", pngImage, output, new ImageSettings(width = Some(50), quality = Some(0.9f)))
    // Should just ignore the quality setting - for now at least.
    checkImage(data(output), "png", 50, 31)
  }

  test("Transform jpeg") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(50), quality = Some(0.7f)))
    assert(output.size > 0)
    checkImage(data(output), "jpeg", 50, 31)
  }

  test("Resize given width only") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(50)))
    checkImage(data(output), "jpeg", 50, 31)
  }

  test("Request resize to original size with no change in quality") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(320), height = Some(200)))
    checkImage(data(output), "jpeg", 320, 200)
  }

  test("Request resize to original size with change in quality") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(320), height = Some(200), quality = Some(0.99f)))
    checkImage(data(output), "jpeg", 320, 200)
  }

  test("Resize given height only") {
    val output = new ByteArrayOutputStream()
    processor.transform("png", pngImage, output, new ImageSettings(height = Some(50)))
    checkImage(data(output), "png", 80, 50)
  }

  test("Resize by stretching") {
    // We should always get the requested size image back when stretching.
    Seq((80, 50), (50, 50), (50, 20)).foreach {
      case (w: Int, h: Int) =>
        val output = new ByteArrayOutputStream()
        var called = false
        val callback = (effectiveSettings: ImageSettings) => {
          assert(effectiveSettings.width === Some(w))
          assert(effectiveSettings.height === Some(h))
          assert(effectiveSettings.quality === Some(ThreadPoolImageProcessor.DefaultQuality))
          assert(effectiveSettings.gravity === Some(Gravity.Center))
          called = true
        }
        processor.transform("jpeg", jpegImage, output,
          new ImageSettings(width = Some(w), height = Some(h), mode = Some(Stretch)), imageCallback = Some(callback))
        checkImage(data(output), "jpeg", w, h)
        assert(called, "Should have called callback with image details")
    }
  }

  test("Resize by cropping") {
    // We should always get the requested size image back when cropping, irrespective of gravity setting.
    Seq((80, 50), (50, 50), (20, 50)).foreach {
      case (w: Int, h: Int) =>
        for (g <- Gravity.values) {
          val output = new ByteArrayOutputStream()
          var called = false
          val callback = (effectiveSettings: ImageSettings) => {
            assert(effectiveSettings.width === Some(w))
            assert(effectiveSettings.height === Some(h))
            assert(effectiveSettings.quality === Some(ThreadPoolImageProcessor.DefaultQuality))
            called = true
          }
          processor.transform("jpeg", jpegImage, output,
            new ImageSettings(width = Some(w), height = Some(h), mode = Some(Crop), gravity = Some(g)), imageCallback = Some(callback))
          checkImage(data(output), "jpeg", w, h)
          assert(called, "Should have called callback with image details")
        }
    }
  }

  test("Resize by scaling") {
    // When scaling, we create an image that fits into the bounding box of the specified size, 
    // that will be smaller when the aspect ratio of the original image and requested size is different.
    Map(
      (320, 200) -> (320, 200),
      (640, 400) -> (640, 400),
      (80, 70) -> (80, 50),
      (50, 50) -> (50, 31),
      (20, 50) -> (20, 13)).foreach {
        case ((inputWidth, inputHeight), (outputWidth, outputHeight)) =>
          val output = new ByteArrayOutputStream()
          processor.transform("jpeg", jpegImage, output,
            new ImageSettings(width = Some(inputWidth), height = Some(inputHeight), mode = Some(Scale)))
          checkImage(data(output), "jpeg", outputWidth, outputHeight)
      }
  }

  test("Convert image to GIF") {
    val output = new ByteArrayOutputStream()
    processor.transform("gif", pngImage, output, new ImageSettings(width = Some(640)))
    assert(output.size > 0)
    checkImage(data(output), "gif", 640, 400)
  }

  test("Change jpeg quality settings only") {
    val output = new ByteArrayOutputStream()
    processor.transform("jpeg", jpegImage, output, new ImageSettings(quality = Some(0.5f)))
    checkImage(data(output), "jpeg", 320, 200)
  }

  test("Crop positions using gravity") {
    import Gravity._
    import ThreadPoolImageProcessor._

    // Cases where the height is unchanged.
    assert(cropPosition(300, 200, 100, 200, Gravity.Center) === (100, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.North) === (100, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.South) === (100, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.East) === (200, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.NorthEast) === (200, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.SouthEast) === (200, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.SouthWest) === (0, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.West) === (0, 0))
    assert(cropPosition(300, 200, 100, 200, Gravity.NorthWest) === (0, 0))

    // Cases where the width is unchanged.
    assert(cropPosition(200, 300, 200, 100, Gravity.North) === (0, 0))
    assert(cropPosition(200, 300, 200, 100, Gravity.South) === (0, 200))
    assert(cropPosition(200, 300, 200, 100, Gravity.East) === (0, 100))
    assert(cropPosition(200, 300, 200, 100, Gravity.West) === (0, 100))
    assert(cropPosition(200, 300, 200, 100, Gravity.Center) === (0, 100))
    assert(cropPosition(200, 300, 200, 100, Gravity.NorthEast) === (0, 0))
    assert(cropPosition(200, 300, 200, 100, Gravity.NorthWest) === (0, 0))
    assert(cropPosition(200, 300, 200, 100, Gravity.SouthEast) === (0, 200))
    assert(cropPosition(200, 300, 200, 100, Gravity.SouthWest) === (0, 200))

    // Cases where both change.
    assert(cropPosition(300, 300, 100, 100, Gravity.North) === (100, 0))
    assert(cropPosition(300, 300, 100, 100, Gravity.South) === (100, 200))
    assert(cropPosition(300, 300, 100, 100, Gravity.Center) === (100, 100))
    assert(cropPosition(300, 300, 100, 100, Gravity.East) === (200, 100))
    assert(cropPosition(300, 300, 100, 100, Gravity.West) === (0, 100))
    assert(cropPosition(300, 300, 100, 100, Gravity.NorthEast) === (200, 0))
    assert(cropPosition(300, 300, 100, 100, Gravity.NorthWest) === (0, 0))
    assert(cropPosition(300, 300, 100, 100, Gravity.SouthEast) === (200, 200))
    assert(cropPosition(300, 300, 100, 100, Gravity.SouthWest) === (0, 200))

  }

}
