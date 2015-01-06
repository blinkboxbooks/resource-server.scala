package com.blinkboxbooks.resourceserver

import java.io.ByteArrayInputStream
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class RangeTest extends FunSuite {

  import Range._

  test("no byte range") {
    assert(Range(None) === Range.unlimited)
  }

  test("supported byte ranges") {
    assert(Range(Some("bytes=0-0")) === Range(Some(0), Some(1l)))
    assert(Range(Some("bytes=0-1")) === Range(Some(0), Some(2l)))
    assert(Range(Some("bytes=123-456")) === Range(Some(123l), Some(456l - 123 + 1)))
    assert(Range(Some("bytes=123-")) === Range(Some(123l), None))
  }

  test("valid but unsupported suffix byte ranges") {
    assert(Range(Some("bytes=-0")) === Range.unlimited)
    assert(Range(Some("bytes=-1")) === Range.unlimited)
    assert(Range(Some("bytes=-123")) === Range.unlimited)
  }

  test("valid but unsupported multiple byte ranges") {
    // These aren't supported at the moment.
    assert(Range(Some("bytes=0-1,2-3")) === Range.unlimited)
    assert(Range(Some("bytes=-1,2-3")) === Range.unlimited)
    assert(Range(Some("bytes=-1,2-3,5-")) === Range.unlimited)
    assert(Range(Some("bytes=-1,2-3,5-6")) === Range.unlimited)
  }

  test("byte ranges with invalid syntax") {
    // Invalid range strings are ignored.
    assert(Range(Some("")) === Range.unlimited)
    assert(Range(Some("byte=-")) === Range.unlimited)
    assert(Range(Some("byte=1-3")) === Range.unlimited)
    assert(Range(Some("bytes=")) === Range.unlimited)
    assert(Range(Some("bytes=xyz")) === Range.unlimited)
    assert(Range(Some("bytes=1.2")) === Range.unlimited)
    assert(Range(Some("bytes=1.2-3")) === Range.unlimited)
    assert(Range(Some("bytes=-3-4")) === Range.unlimited)
    assert(Range(Some("bytes=3-4-5")) === Range.unlimited)
  }

  test("byte ranges with negative length") {
    assert(Range(Some("byte=1-3")) === Range.unlimited)
  }

  test("bounded input stream") {
    val input = "0123456"
    val testCases = Map(
      Range.unlimited -> input,
      new Range(Some(0), None) -> input,
      new Range(Some(0), Some(0)) -> "",
      new Range(Some(0), Some(3)) -> "012",
      new Range(Some(2), Some(3)) -> "234",
      new Range(Some(5), Some(3)) -> "56",
      new Range(Some(2), None) -> "23456",
      new Range(Some(99999), None) -> "")

    testCases.foreach {
      case (range, expected) =>
        val in = new ByteArrayInputStream(input.getBytes)
        val bounded = boundedInputStream(in, range)
        val result = Source.fromInputStream(bounded).getLines().mkString
        assert(result === expected, s"Result for range $range should be $expected, got $result")
    }
  }

}
