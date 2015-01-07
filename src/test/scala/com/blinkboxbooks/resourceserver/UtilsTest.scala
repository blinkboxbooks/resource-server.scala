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
