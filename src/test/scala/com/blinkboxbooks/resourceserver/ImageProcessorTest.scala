package com.blinkboxbooks.resourceserver

import java.io._
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

object ImageProcessorTest {
  // Fixed test data.
  val jpegData = IOUtils.toByteArray(getClass.getResourceAsStream("/test.jpeg"))
  val pngData = IOUtils.toByteArray(getClass.getResourceAsStream("/test.png"))
}

@RunWith(classOf[JUnitRunner])
class ImageProcessorTest extends FunSuite with BeforeAndAfter with ImageChecks {

  import ImageProcessorTest._

  var processor: ImageProcessor = _
  var jpegImage: InputStream = _
  var pngImage: InputStream = _
  var output: ByteArrayOutputStream = _

  before {
    jpegImage = new ByteArrayInputStream(jpegData)
    pngImage = new ByteArrayInputStream(pngData)
    output = new ByteArrayOutputStream()
    processor = new SynchronousScalrImageProcessor()
  }

  test("No image settings given") {
    intercept[IllegalArgumentException](processor.transform("jpeg", jpegImage, output, new ImageSettings()))
  }

  test("Unknown image format") {
    intercept[IllegalArgumentException](processor.transform("invalid", jpegImage, output, new ImageSettings()))
  }

  def outputData = new ByteArrayInputStream(output.toByteArray())

  test("Transform png") {
    processor.transform("png", pngImage, output, new ImageSettings(Some(50), Some(40), Some(Scale), None))
    assert(output.size > 0)
    // When scaling, the image ration is retained, hence the requested height isn't taken into account.
    checkImage(outputData, "png", 50, 31)
    checkImageContent(outputData, "/50x31.png")
  }

  test("Try to specify quality setting for png file") {
    processor.transform("png", pngImage, output, new ImageSettings(Some(50), Some(40), Some(Scale), Some(0.9f)))
    // Should just ignore the quality setting - for now at least.
    checkImage(outputData, "png", 50, 31)
    checkImageContent(outputData, "/50x31.png")
  }

  test("Transform jpeg") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(Some(50), Some(40), Some(Scale), Some(0.7f)))
    assert(output.size > 0)
    checkImage(outputData, "jpeg", 50, 31)
    checkImageContent(outputData, "/50x31.jpeg")
  }

  test("Resize given width only") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(50)))
    checkImage(outputData, "jpeg", 50, 31) // What's the exact size to expect here?
    checkImageContent(outputData, "/50x31.jpeg")
  }

  ignore("Resize given height only") {
    // TODO: Need to figure out WTF the height is comes out so big here!
    processor.transform("png", pngImage, output, new ImageSettings(height = Some(50)))
    checkImage(outputData, "png", 42, 50) // What's the exact size to expect here?
    checkImageContent(outputData, "/height50.jpeg")
  }

  ignore("Resize by cropping") {
    // Specify sizes that give a different form factor then check the results.
    fail("TODO")
  }

  ignore("Resize by stretching") {
    fail("TODO")
  }

  ignore("Resize jpeg to bigger than original") {
    fail("TODO")
  }

  ignore("Resize png to bigger than original") {
    fail("TODO")
  }

  ignore("Transcode png to jpeg") {
    fail("TODO")
  }

  ignore("Change jpeg quality settings only") {
    fail("TODO")
  }

}
