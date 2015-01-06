package com.blinkboxbooks.resourceserver

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.input.BoundedInputStream
import java.io.InputStream
import java.awt.image.BufferedImage
import scala.util.Try
import resource.Resource

/**
 * The traditional bag o' stuff that doesn't quite fit in anywhere else.
 */
object Utils {

  /** Return hash of given string in hex format. */
  def stringHash(str: String) = DigestUtils.md5Hex(str)

  /** Make BufferedImages managed resources so they can be automatically flushed when no longer used. */
  implicit def pooledConnectionResource[A <: BufferedImage] = new Resource[A] {
    override def close(r: A) = r.flush()
    override def toString = "Resource[java.awt.image.BufferedImage]"
  }

  /**
   * Given an InputStream, skip the necessary number of bytes in it, and
   * return an InputStream that will only read bytes up to the given limit.
   */
  def boundedInputStream(inputStream: InputStream, range: Range) = {
    // Skip bytes if there's an offset.
    range.offset.foreach(offset => inputStream.skip(offset))
    // Limit the number of bytes read if there's a limit.
    range.limit match {
      case None => inputStream
      case Some(limit) => new BoundedInputStream(inputStream, limit)
    }
  }

  def canonicalUri(baseFilename: String, imageSettings: ImageSettings) = {
    var params = Map("v" -> "0")
    imageSettings.height.foreach(h => params += ("img:h" -> h.toString))
    imageSettings.width.foreach(w => params += ("img:w" -> w.toString))
    imageSettings.quality.foreach(q => params += ("img:q" -> (q * 100).toInt.toString))
    imageSettings.mode match {
      case Some(Crop) =>
        params += ("img:m" -> "crop")
        imageSettings.gravity.foreach(g => params += ("img:g" -> g.toString))
      case Some(Stretch) => params += ("img:m" -> "stretch")
      case Some(ScaleWithoutUpscale) => params += ("img:m" -> "scale")
      case Some(ScaleWithUpscale) => params += ("img:m" -> "scale!")
      case _ =>
    }

    val sortedParams = params.toList.sortBy(_._1)
    "/params;" + paramsToString(sortedParams) + "/" + baseFilename
  }

  def paramsToString(params: List[(String, String)]): String =
    params.map { case (key, value) => key.toString + "=" + value.toString }.mkString(";")

  /**
   * @returns a pair of (original extension, target extension).
   * The former contains the extension of the file (in lower case), if present, otherwise None.
   * The latter contains the target extension (in lower case) in a request for image conversion, if requested, otherwise None.
   */
  def fileExtension(filename: String): (Option[String], Option[String]) = {
    filename.split("\\.").reverse.toList match {
      case ext2 :: ext1 :: filenamePart :: _ if acceptedFormat(ext1) && producableFormat(ext2) => (Some(ext1.toLowerCase), Some(ext2.toLowerCase))
      case ext :: filenamePart :: _ => (Some(ext.toLowerCase), None)
      case _ => (None, None)
    }
  }

  def acceptedFormat(extension: String) = ACCEPTED_IMAGE_FORMATS.contains(extension.toLowerCase)
  def producableFormat(extension: String) = PRODUCABLE_IMAGE_FORMATS.contains(extension.toLowerCase)

  val ACCEPTED_IMAGE_FORMATS = Set("png", "jpg", "jpeg", "gif", "svg", "tif", "tiff", "bmp")
  private val PRODUCABLE_IMAGE_FORMATS = Set("png", "jpg", "jpeg", "gif")

}

