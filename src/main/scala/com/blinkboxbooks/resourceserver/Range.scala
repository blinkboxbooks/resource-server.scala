package com.blinkboxbooks.resourceserver

import java.io.InputStream
import org.apache.commons.io.input.BoundedInputStream
import scala.util.Try

/** Value class that represents the offset of limit in prefix byte ranges. */
case class Range(offset: Long, limit: Option[Long])

object Range {

  /**
   * Get offset and limit based on parsing HTTP Range parameter as specified
   * in RFC 2616.
   *
   * This implementation only supports a single range, and does not support suffix ranges.
   *
   * @return (offset, limit). The offset is 0 if not specified, the limit is None of not specified,
   * as a limit of 0 has a different meaning to "no limit".
   */
  def apply(rangeExpr: Option[String]): Option[Range] = rangeExpr.flatMap {
    case RangePattern(startStr, endStr) => parseRange(startStr, endStr)
    case _                              => None
  }

  /**
   * Given an InputStream, skip the necessary number of bytes in it, and
   * return an InputStream that will only read bytes up to the given limit.
   */
  def boundedInputStream(inputStream: InputStream, range: Option[Range]) = {
    // Skip bytes if there's an offset.
    range.foreach(r => inputStream.skip(r.offset))
    // Limit the number of bytes read if there's a limit.
    range.flatMap(_.limit) match {
      case None        => inputStream
      case Some(limit) => new BoundedInputStream(inputStream, limit)
    }
  }

  private val RangePattern = """^bytes=(\d+)-(\d*)$""".r

  private def parseRange(startStr: String, endStr: String): Option[Range] = (for {
    start <- parseLong(startStr)
    end <- parseOptionalLong(endStr)
  } yield Range(start, limit(start, end))).toOption

  private def parseLong(str: String): Try[Long] = Try(str.toLong)

  private def parseOptionalLong(str: String): Try[Option[Long]] =
    Try(if (str == "") None else Some(str.toLong))

  private def limit(start: Long, end: Option[Long]) = end match {
    case Some(end) if end >= start => Some(end - start + 1)
    case None                      => None
  }

}
