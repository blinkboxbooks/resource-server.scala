package com.blinkboxbooks.resourceserver

import java.io._
import org.imgscalr.Scalr
import org.imgscalr.Scalr._
import javax.imageio._
import javax.imageio.stream._
import javax.imageio.plugins.jpeg.JPEGImageWriteParam

sealed abstract class ResizeMode
case object Scale extends ResizeMode
case object Crop extends ResizeMode
case object Stretch extends ResizeMode

/** Value class for transforming images. */
case class ImageSettings(width: Option[Int] = None, height: Option[Int] = None,
  mode: Option[ResizeMode] = None, quality: Option[Float] = None) {

  if (quality.isDefined && (quality.get < 0.0f || quality.get > 1.0f))
    throw new IllegalArgumentException("Quality setting must be between 0.0 and 1.0")

  def hasSettings = width.isDefined || height.isDefined || quality.isDefined
}

trait ImageProcessor {

  /**
   * Resize a given image.
   *
   *  @param input              An input stream with the binary data of the image. Will not be closed by this method.
   *  @param output             An output stream that the converted image will be written to. Will not be closed by this method.
   *  @param outputFiletype     A string such as "jpg" or "png" that describes the type of file format to write the resize image to.
   *                            See @see javax.imageio.spi.ImageWriterSpi#getFormatNames for valid format strings.
   *  @param resizeSettings     Settings for the converted image.
   *
   *  @throws IllegalArgumentException if the given filetype is unknown.
   */
  def transform(fileType: String, input: InputStream, output: OutputStream, resizeSettings: ImageSettings)

}

class SynchronousScalrImageProcessor extends ImageProcessor with TimeLogging {

  // Disables disk caching for image files, makes reading image files faster.
  ImageIO.setUseCache(false)

  override def transform(outputFileType: String, input: InputStream, output: OutputStream, settings: ImageSettings) {

    if (!settings.hasSettings) throw new IllegalArgumentException("No image settings given")

    // TODO: Retain size ratio if only one of these is set.
    // And, deal with crop & stretch properly.
    val width = settings.width.getOrElse(200)
    val height = settings.height.getOrElse(100)
    val resizeMode = settings.mode.getOrElse(Scale) match {
      case Scale => Mode.AUTOMATIC
      case Crop => Mode.AUTOMATIC
      case Stretch => Mode.FIT_EXACT
    }

    // Read the original image.
    val originalImage = time("reading image") { ImageIO.read(input) }
    if (originalImage == null) throw new IOException(s"Unable to decode image of type $outputFileType")

    try {
      // Resize the image.
      val resizedImage = time("resize") { Scalr.resize(originalImage, Method.BALANCED, resizeMode, width, height, Scalr.OP_ANTIALIAS) }
      try {
        // Get an image writer for the given file type.
        val writers = ImageIO.getImageWritersByFormatName(outputFileType)
        if (!writers.hasNext) throw new IllegalArgumentException(s"Unknown file type '$outputFileType'")
        val writer = writers.next

        // Set output image parameters.
        val imageParams = writer.getDefaultWriteParam()
        if (settings.quality.isDefined && imageParams.canWriteCompressed()) {
          imageParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
          imageParams.setCompressionQuality(settings.quality.get)
        }
        writer.setOutput(new MemoryCacheImageOutputStream(output))

        // Write the output in the configured format.
        time("writing image") { writer.write(null, new IIOImage(resizedImage, null, null), imageParams) }
      } finally {
        resizedImage.flush()
      }
    } finally {
      originalImage.flush()
    }
  }
}