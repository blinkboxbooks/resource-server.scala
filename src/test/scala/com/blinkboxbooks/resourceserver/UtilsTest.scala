package com.blinkboxbooks.resourceserver

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

}
