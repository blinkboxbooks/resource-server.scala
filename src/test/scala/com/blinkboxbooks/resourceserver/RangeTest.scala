package com.blinkboxbooks.resourceserver

import java.io.ByteArrayInputStream
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class RangeTest extends FunSuite {

  import Range._

  test("supported byte ranges") {
    assert(Range(Some("bytes=0-0")) === Some(Range(0, Some(1l))))
    assert(Range(Some("bytes=0-1")) === Some(Range(0, Some(2l))))
    assert(Range(Some("bytes=123-456")) === Some(Range(123l, Some(456l - 123 + 1))))
    assert(Range(Some("bytes=123-")) === Some(Range(123l, None)))
  }

  test("valid but unsupported suffix byte ranges") {
    assert(Range(Some("bytes=-0")) === None)
    assert(Range(Some("bytes=-1")) === None)
    assert(Range(Some("bytes=-123")) === None)
  }

  test("valid but unsupported multiple byte ranges") {
    // These aren't supported at the moment.
    assert(Range(Some("bytes=0-1,2-3")) === None)
    assert(Range(Some("bytes=-1,2-3")) === None)
    assert(Range(Some("bytes=-1,2-3,5-")) === None)
    assert(Range(Some("bytes=-1,2-3,5-6")) === None)
  }

  test("byte ranges with invalid syntax") {
    // Invalid range strings are ignored.
    assert(Range(Some("")) === None)
    assert(Range(Some("byte=-")) === None)
    assert(Range(Some("byte=1-3")) === None)
    assert(Range(Some("bytes=")) === None)
    assert(Range(Some("bytes=xyz")) === None)
    assert(Range(Some("bytes=1.2")) === None)
    assert(Range(Some("bytes=1.2-3")) === None)
    assert(Range(Some("bytes=-3-4")) === None)
    assert(Range(Some("bytes=3-4-5")) === None)
  }

  test("byte ranges with negative length") {
    assert(Range(Some("byte=1-3")) === None)
  }

  test("bounded input stream") {
    val input = "0123456"
    val testCases = Map(
      new Range(0, None) -> input,
      new Range(0, Some(0)) -> "",
      new Range(0, Some(3)) -> "012",
      new Range(2, Some(3)) -> "234",
      new Range(5, Some(3)) -> "56",
      new Range(2, None) -> "23456",
      new Range(99999, None) -> "")

    testCases.foreach {
      case (range, expected) =>
        val in = new ByteArrayInputStream(input.getBytes)
        val bounded = boundedInputStream(in, Some(range))
        val result = Source.fromInputStream(bounded).getLines().mkString
        assert(result === expected, s"Result for range $range should be $expected, got $result")
    }
  }

  test("unbounded input stream") {
    val input = "0123456"
    val in = new ByteArrayInputStream(input.getBytes)
    val bounded = boundedInputStream(in, None)
    val result = Source.fromInputStream(bounded).getLines().mkString
    // Should get input unchanged.
    assert(result === input)
  }

}
