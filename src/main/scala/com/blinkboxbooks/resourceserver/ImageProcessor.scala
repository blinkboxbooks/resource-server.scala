package com.blinkboxbooks.resourceserver

import java.io._
import javax.imageio._
import javax.imageio.stream._
import java.awt.image.BufferedImage
import resource._
import Utils._
import org.imgscalr.Scalr
import org.imgscalr.Scalr._
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

/** Value class for transforming images. */
case class ImageSettings(width: Option[Int] = None, height: Option[Int] = None,
  mode: Option[ResizeMode] = None, quality: Option[Float] = None) {

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

      val resizeMode = settings.mode.getOrElse(Scale) match {
        case Scale => Mode.AUTOMATIC
        case Crop => Mode.AUTOMATIC
        case Stretch => Mode.FIT_EXACT
      }

      // Resize the image if requested. 
      // #TODO: May have to do cropping as a separate step?
      for (
        image <- managed(time("resize", Debug) {
          settings match {
            case ImageSettings(Some(width), None, _, _) => resize(originalImage, Mode.FIT_TO_WIDTH, width)
            case ImageSettings(None, Some(height), _, _) => resize(originalImage, Mode.FIT_TO_HEIGHT, height)
            case ImageSettings(Some(width), Some(height), _, _) => resize(originalImage, resizeMode, width, height)
            case _ => originalImage
          }
        })
      ) {
        // Write the resulting image in the desired format.
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
    Await.result(Future { Scalr.resize(src, Method.BALANCED, mode, targetSize, Scalr.OP_ANTIALIAS) }, timeout)

  private def resize(src: BufferedImage, mode: Mode, width: Int, height: Int) =
    Await.result(Future { Scalr.resize(src, Method.BALANCED, mode, width, height, Scalr.OP_ANTIALIAS) }, timeout)

}