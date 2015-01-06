package com.blinkboxbooks.resourceserver

import java.io.InputStream
import org.apache.commons.io.input.BoundedInputStream
import scala.util.Try

/** Value class for specifying optional limits. */
case class Range(offset: Option[Long], limit: Option[Long]) {
  def isUnlimited: Boolean = this == Range.unlimited
}

object Range {

  val unlimited = Range(None, None)

  /**
   * Get offset and limit based on parsing HTTP Range parameter as specified
   * in RFC 2616.
   *
   * This implementation only supports a single range, and does not support suffix ranges.
   *
   * @return (offset, limit). The offset is 0 if not specified, the limit is None of not specified,
   * as a limit of 0 has a different meaning to "no limit".
   */
  def apply(rangeExpr: Option[String]): Range = rangeExpr.flatMap {
    case RangePattern(startStr, endStr) => parseRange(startStr, endStr)
    case _                              => None
  }.getOrElse(Range.unlimited)

  /**
   * Given an InputStream, skip the necessary number of bytes in it, and
   * return an InputStream that will only read bytes up to the given limit.
   */
  def boundedInputStream(inputStream: InputStream, range: Range) = {
    // Skip bytes if there's an offset.
    range.offset.foreach(offset => inputStream.skip(offset))
    // Limit the number of bytes read if there's a limit.
    range.limit match {
      case None        => inputStream
      case Some(limit) => new BoundedInputStream(inputStream, limit)
    }
  }

  private val RangePattern = """^bytes=(\d+)-(\d*)$""".r

  private def parseRange(startStr: String, endStr: String): Option[Range] = (for {
    start <- parseOptionalLong(startStr)
    end <- parseOptionalLong(endStr)
  } yield Range(start, limit(start, end))).toOption

  private def parseOptionalLong(str: String): Try[Option[Long]] =
    Try(if (str == "") None else Some(str.toLong))

  private def limit(start: Option[Long], end: Option[Long]) = (start, end) match {
    case (Some(start), Some(end)) if end >= start => Some(end - start + 1)
    case (Some(start), None)                      => None
    case (None, Some(end))                        => Some(end + 1)
    case _                                        => None
  }

}
