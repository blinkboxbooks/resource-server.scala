package com.blinkboxbooks.resourceserver
import java.io.ByteArrayInputStream
import scala.io.Source
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

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

  test("Canonical URI with no parameters") {
    assert(canonicalUri("foo/bar.baz", new ImageSettings(gravity = None)) === "/params;v=0/foo/bar.baz")
  }

  test("Canonical URI with full set of parameters, not cropped") {
    val settings = new ImageSettings(height = Some(100), width = Some(140), quality = Some(0.42f), gravity = Some(Gravity.NorthWest),
      mode = Some(ScaleWithoutUpscale))
    assert(canonicalUri("foo/bar.baz", settings) === "/params;img:h=100;img:m=scale;img:q=42;img:w=140;v=0/foo/bar.baz")
  }

  test("Canonical URI with full set of parameters, cropped") {
    val settings = new ImageSettings(height = Some(100), width = Some(140), quality = Some(0.42f), gravity = Some(Gravity.NorthWest),
      mode = Some(Crop))
    assert(canonicalUri("foo/bar.baz", settings) === "/params;img:g=nw;img:h=100;img:m=crop;img:q=42;img:w=140;v=0/foo/bar.baz")
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

  test("get extension for filenames with no format conversion") {
    assert(fileExtension("foo.t") === (Some("t"), None))
    assert(fileExtension("f.t") === (Some("t"), None))
    assert(fileExtension("foo.html") === (Some("html"), None))
    assert(fileExtension("foo.Html") === (Some("html"), None))
    assert(fileExtension("foo.HTML") === (Some("html"), None))
    assert(fileExtension("foo.txt") === (Some("txt"), None))
    assert(fileExtension("foo.bar.txt") === (Some("txt"), None))
  }

  test("get extension for filenames with no extension") {
    assert(fileExtension("") === (None, None))
    assert(fileExtension("f") === (None, None))
    assert(fileExtension("foo") === (None, None))
    assert(fileExtension("foo.") === (None, None))
    assert(fileExtension("foo-bar") === (None, None))
  }

  test("get extension for filenames with format conversion") {
    assert(fileExtension("x.jpg.png") === (Some("jpg"), Some("png")))
    assert(fileExtension("foo.jpg.png") === (Some("jpg"), Some("png")))
    assert(fileExtension("foo.bar.jpg.png") === (Some("jpg"), Some("png")))

    assert(fileExtension("foo.jpeg.png") === (Some("jpeg"), Some("png")))
    assert(fileExtension("foo.png.jpeg") === (Some("png"), Some("jpeg")))
    assert(fileExtension("foo.jpeg.gif") === (Some("jpeg"), Some("gif")))
    assert(fileExtension("foo.gif.jpeg") === (Some("gif"), Some("jpeg")))
    assert(fileExtension("foo.JPEG.GIF") === (Some("jpeg"), Some("gif")))

    // What about these cases - what's an extension and what's not? Only known extensions?
    // Or suitably short extensions?
    assert(fileExtension("foo.bar.png") === (Some("png"), None))
  }

}
