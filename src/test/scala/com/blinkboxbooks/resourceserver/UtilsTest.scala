package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.StringReader
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class UtilsTest extends FunSuite {

  import Utils._

  test("MD5 hashes match Ruby version") {
    // Check that the hash values in this version are compatible with the hashes
    // produced by the Ruby version. The expected values below are the result from Ruby code.
    assert(stringHash("") == "d41d8cd98f00b204e9800998ecf8427e")
    assert(stringHash("foo") == "acbd18db4cc2f85cedef654fccc4a4d8")
    assert(stringHash("foo bar") == "327b6f07435811239bc47e1544353273")
    assert(stringHash("http://some/path?foo=42") == "a3359beeee73615af0832b17da5dffe4")
  }

  test("no byte range") {
    assert(range(None) === Range.unlimited)
  }

  test("valid single byte ranges") {
    assert(range(Some("bytes=0-0")) === Range(Some(0), Some(1l)))
    assert(range(Some("bytes=0-1")) === Range(Some(0), Some(2l)))
    assert(range(Some("bytes=-1")) === Range(None, Some(2l)))
    assert(range(Some("bytes=123-456")) === Range(Some(123l), Some(456l - 123 + 1)))
    assert(range(Some("bytes=123-")) === Range(Some(123l), None))
  }

  test("valid multiple byte ranges") {
    // These aren't supported at the moment.
    assert(range(Some("bytes=0-1,2-3")) === Range.unlimited)
    assert(range(Some("bytes=-1,2-3")) === Range.unlimited)
    assert(range(Some("bytes=-1,2-3,5-")) === Range.unlimited)
    assert(range(Some("bytes=-1,2-3,5-6")) === Range.unlimited)
  }

  test("byte ranges with invalid syntax") {
    // Invalid range strings are ignored.
    assert(range(Some("")) === Range.unlimited)
    assert(range(Some("byte=1-3")) === Range.unlimited)
    assert(range(Some("bytes=")) === Range.unlimited)
    assert(range(Some("bytes=xyz")) === Range.unlimited)
    assert(range(Some("bytes=1.2")) === Range.unlimited)
    assert(range(Some("bytes=1.2-3")) === Range.unlimited)
    assert(range(Some("bytes=-3-4")) === Range.unlimited)
    assert(range(Some("bytes=3-4-5")) === Range.unlimited)
  }

  test("byte ranges with negative length") {
    assert(range(Some("byte=1-3")) === Range.unlimited)
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
