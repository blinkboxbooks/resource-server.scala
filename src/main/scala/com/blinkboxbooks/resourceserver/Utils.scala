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

}
