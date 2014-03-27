package com.blinkboxbooks.resourceserver

import java.io._
import javax.imageio._
import javax.imageio.stream._
import java.awt.image.BufferedImage
import resource._
import Utils._
import org.imgscalr.Scalr
import org.imgscalr.Scalr.resize
import org.imgscalr.Scalr.Mode
import org.imgscalr.Scalr.Mode._
import org.imgscalr.Scalr.Method
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

/** Types for each of the possible ways to resize an image. */
sealed abstract class ResizeMode
case object Scale extends ResizeMode
case object Crop extends ResizeMode
case object Stretch extends ResizeMode

/** Enumeration for gravity setting, that controls what part of an image is cropped. */
object Gravity extends Enumeration {
  type Gravity = Value
  val Center = Value("c")
  val North = Value("n")
  val NorthEast = Value("ne")
  val East = Value("e")
  val SouthEast = Value("se")
  val South = Value("s")
  val SouthWest = Value("sw")
  val West = Value("w")
  val NorthWest = Value("nw")
}

import Gravity._

/** Value class for transforming images. */
case class ImageSettings(width: Option[Int] = None, height: Option[Int] = None,
  mode: Option[ResizeMode] = None, quality: Option[Float] = None, gravity: Option[Gravity] = Some(Center)) {

  if (quality.isDefined && (quality.get < 0.0f || quality.get > 1.0f))
    throw new IllegalArgumentException("Quality setting must be between 0.0 and 1.0")

  def hasSettings = width.isDefined || height.isDefined || quality.isDefined
}

/**
 * Common interface for transforming images.
 */
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
   *  @throws Exception if the given filetype is unknown.
   */
  def transform(fileType: String, input: InputStream, output: OutputStream, resizeSettings: ImageSettings)

}

/**
 * Implementation of image processor that uses the imgscalr AsyncScalr class to perform
 * image processing in a thread pool with a limited number of threads.
 */
class ThreadPoolImageProcessor(threadCount: Int) extends ImageProcessor with TimeLogging {

  // Execute conversions in a fixed size thread pool, to limit the number of concurrent jobs,
  // hence guarding against running out of memory under heavy load.
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadCount))
  implicit val timeout = 10 seconds

  // Disables disk caching for image files, makes reading image files faster.
  ImageIO.setUseCache(false)

  override def transform(outputFileType: String, input: InputStream, output: OutputStream, settings: ImageSettings) {

    // Read the original image.
    for (originalImage <- managed(time("reading image", Debug) { ImageIO.read(input) })) {

      if (originalImage == null) throw new IOException(s"Unable to decode image of type $outputFileType")

      // Resize the image if a new size has been requested.
      for (
        image <- managed(time("resize", Debug) {
          settings match {
            case ImageSettings(Some(width), None, _, _, _) => resize(originalImage, FIT_TO_WIDTH, width)
            case ImageSettings(None, Some(height), _, _, _) => resize(originalImage, FIT_TO_HEIGHT, height)
            case ImageSettings(Some(width), Some(height), Some(Stretch), _, gravity) => resize(originalImage, FIT_EXACT, width, height)
            case ImageSettings(Some(width), Some(height), Some(Scale), _, gravity) => resize(originalImage, AUTOMATIC, width, height)
            case ImageSettings(Some(width), Some(height), Some(Crop), _, gravity) =>
              // First resize to an image that retains the smallest dimension requested, the crop of the excess.
              val originalRatio = (originalImage.getHeight.asInstanceOf[Float] / originalImage.getWidth)
              val requestedRatio = (height.asInstanceOf[Float] / width)
              val resizeMode = if (requestedRatio < originalRatio) FIT_TO_WIDTH else FIT_TO_HEIGHT
              val resized = resize(originalImage, resizeMode, width, height)
              crop(resized, width, height, gravity getOrElse Center)
            case _ => originalImage
          }
        })
      ) {
        // Convert the resulting image to the desired format.
        val writers = ImageIO.getImageWritersByFormatName(outputFileType)
        if (!writers.hasNext) throw new Exception(s"Unknown file type '$outputFileType'")
        val writer = writers.next

        val imageParams = writer.getDefaultWriteParam()
        if (settings.quality.isDefined && imageParams.canWriteCompressed()) {
          imageParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
          imageParams.setCompressionQuality(settings.quality.get)
        }

        val imageOutputStream = new MemoryCacheImageOutputStream(output)
        writer.setOutput(imageOutputStream)
        time("writing image", Debug) { writer.write(null, new IIOImage(image, null, null), imageParams) }
        imageOutputStream.close()
      }
    }
  }

  private def resize(src: BufferedImage, mode: Mode, targetSize: Int) =
    Await.result(Future {
      Scalr.resize(src, Method.BALANCED, mode, targetSize, Scalr.OP_ANTIALIAS)
    }, timeout)

  private def resize(src: BufferedImage, mode: Mode, width: Int, height: Int) =
    Await.result(Future {
      Scalr.resize(src, Method.BALANCED, mode, width, height, Scalr.OP_ANTIALIAS)
    }, timeout)

  private def crop(src: BufferedImage, targetWidth: Int, targetHeight: Int, gravity: Gravity) = {
    val (x, y) = ThreadPoolImageProcessor.cropPosition(src.getWidth, src.getHeight, targetWidth, targetHeight, gravity)
    time("crop", Debug) { Scalr.crop(src, x, y, targetWidth, targetHeight) }
  }

}

object ThreadPoolImageProcessor {

  /**
   * Calculate the part of an image to crop using a gravity parameter,
   * in a similar fashion to ImageMagick (see http://www.imagemagick.org/Usage/crop/#crop_gravity).
   */
  def cropPosition(originalWidth: Int, originalHeight: Int, targetWidth: Int, targetHeight: Int, gravity: Gravity) = {
    assert(targetWidth > 0 && targetHeight > 0)
    assert(originalWidth >= targetWidth && originalHeight >= targetHeight)
    val x = gravity match {
      case West | SouthWest | NorthWest => 0
      case North | Center | South => (originalWidth - targetWidth) / 2
      case East | SouthEast | NorthEast => originalWidth - targetWidth
    }
    val y = gravity match {
      case South | SouthWest | SouthEast => originalHeight - targetHeight
      case West | Center | East => (originalHeight - targetHeight) / 2
      case North | NorthWest | NorthEast => 0
    }
    (x, y)
  }
}
