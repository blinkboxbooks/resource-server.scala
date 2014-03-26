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
    processor = new ThreadPoolImageProcessor(1)
  }

  test("No image settings given") {
    // Allow this, e.g. for trans-coding.
    processor.transform("jpeg", jpegImage, output, new ImageSettings())
    assert(output.size > 0)
    checkImage(outputData, "jpeg", 320, 200)
  }

  test("Unknown image format") {
    intercept[Exception](processor.transform("invalid", jpegImage, output, new ImageSettings()))
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
    checkImage(outputData, "jpeg", 50, 31)
    checkImageContent(outputData, "/50x31.jpeg")
  }

  test("Resize given height only") {
    // TODO: Need to figure out WTF the width is comes out so big here!
    // I'm not correctly dealing with partial sizes only - original size will have an effect then.
    processor.transform("png", pngImage, output, new ImageSettings(height = Some(50)))
    checkImage(outputData, "png", 80, 50)
    checkImageContent(outputData, "/80x50.jpeg")
  }

  ignore("Resize by cropping") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(50), height = Some(50), mode = Some(Crop)))
    checkImage(outputData, "jpeg", 50, 50)
    checkImageContent(outputData, "/50x50cropped.jpeg")
  }

  test("Resize by stretching") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(50), height = Some(50), mode = Some(Stretch)))
    checkImage(outputData, "jpeg", 50, 50)
    checkImageContent(outputData, "/50x50stretched.jpeg")
  }

  test("Resize jpeg to bigger than original") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(width = Some(640)))
    assert(output.size > 0)
    checkImage(outputData, "jpeg", 640, 400)
    checkImageContent(outputData, "/640x400.jpeg")
  }

  test("Resize png to bigger than original") {
    processor.transform("png", pngImage, output, new ImageSettings(width = Some(640)))
    assert(output.size > 0)
    checkImage(outputData, "png", 640, 400)
    checkImageContent(outputData, "/640x400.png")
  }

  test("Convert image to GIF") {
    processor.transform("gif", pngImage, output, new ImageSettings(width = Some(640)))
    assert(output.size > 0)
    checkImage(outputData, "gif", 640, 400)
    checkImageContent(outputData, "/640x400.gif")
  }

  test("Change jpeg quality settings only") {
    processor.transform("jpeg", jpegImage, output, new ImageSettings(quality = Some(0.5f)))
    checkImage(outputData, "jpeg", 320, 200)
    checkImageContent(outputData, "/320x200.jpeg")
  }

}
