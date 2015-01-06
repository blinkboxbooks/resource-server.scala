package com.blinkboxbooks.resourceserver

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
   * This implementation only supports a single range.
   *
   * @returns (offset, limit). The offset is 0 if not specified, the limit is None of not specified,
   * as a limit of 0 has a different meaning to "no limit".
   */
  def apply(range: Option[String]): Range =
    Try(range.map {
      case RangePattern(startStr, endStr) => {
        val start = parseLong(startStr)
        val end = parseLong(endStr)
        new Range(start, limit(start, end))
      }
      case _ => Range.unlimited
    }).toOption.flatten.getOrElse(Range.unlimited)

  private val RangePattern = """^bytes=(\d+)-(\d*)$""".r

  private def parseLong(str: String) = str match {
    case "" => None
    case _  => Some(str.toLong)
  }

  private def limit(start: Option[Long], end: Option[Long]) = (start, end) match {
    case (Some(start), Some(end)) if end >= start => Some(end - start + 1)
    case (Some(start), None)                      => None
    case (None, Some(end))                        => Some(end + 1)
    case _                                        => None
  }

}
