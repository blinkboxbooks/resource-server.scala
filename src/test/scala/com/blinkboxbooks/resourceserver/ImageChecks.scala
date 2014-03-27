package com.blinkboxbooks.resourceserver

import org.scalatest.Assertions._
import javax.imageio.ImageIO
import java.io.InputStream

trait ImageChecks {

  /**
   * Check that the given data is readable as an image of the specified type,
   * and has the desired dimensions.
   */
  def checkImage(input: InputStream, filetype: String, width: Int, height: Int) {
    val reader = ImageIO.getImageReadersByFormatName(filetype).next
    val iis = ImageIO.createImageInputStream(input)
    reader.setInput(iis)
    val image = reader.read(0)
    assert(image.getWidth() === width, s"Got image of (${image.getWidth}, ${image.getHeight}), expected ($width, $height)")
    assert(image.getHeight() === height, s"Got image of (${image.getWidth}, ${image.getHeight}), expected ($width, $height)")
  }

}
