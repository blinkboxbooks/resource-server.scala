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
      case Some(Scale) => params += ("img:m" -> "scale")
      case _ =>
    }

    val sortedParams = params.toList.sortBy(_._1)
    "/params;" + paramsToString(sortedParams) + "/" + baseFilename
  }

  def paramsToString(params: List[(String, String)]): String =
    params.map { case (key, value) => key.toString + "=" + value.toString }.mkString(";")

  /** Value class for specifying optional limits. */
  case class Range(offset: Option[Long], limit: Option[Long])
  object Range {
    def unlimited = Range(None, None)
  }

  /**
   * Get offset and limit based on parsing HTTP Range parameter as specified
   * in RFC 2616.
   *
   * This implementation only supports a single range.
   *
   * @returns (offset, limit). The offset is 0 if not specified, the limit is None of not specified,
   * as a limit of 0 has a different meaning to "no limit".
   */
  def range(range: Option[String]): Range =
    Try(range.map {
      case RangePattern(startStr, endStr) => {
        val start = parseLong(startStr)
        val end = parseLong(endStr)
        new Range(start, limit(start, end))
      }
      case _ => Range.unlimited
    }).toOption.flatten.getOrElse(Range.unlimited)

  private val RangePattern = """^bytes=(\d*)-(\d*)$""".r

  private def parseLong(str: String) = str match {
    case "" => None
    case _ => Some(str.toLong)
  }

  private def limit(start: Option[Long], end: Option[Long]) = (start, end) match {
    case (Some(start), Some(end)) if end >= start => Some(end - start + 1)
    case (Some(start), None) => None
    case (None, Some(end)) => Some(end + 1)
    case _ => None
  }

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
