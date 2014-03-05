package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import MatrixParameters._

@RunWith(classOf[JUnitRunner])
class MatrixParametersTest extends FunSuite {

  test("Matrix params, valid cases") {
    assert(getMatrixParams("").get === Map())
    assert(getMatrixParams("a=1").get === Map("a" -> "1"))
    assert(getMatrixParams("a=1;").get === Map("a" -> "1"))
    assert(getMatrixParams("x:a=1").get === Map("x:a" -> "1"))
    assert(getMatrixParams("x:abc=1").get === Map("x:abc" -> "1"))
    assert(getMatrixParams("xyz:abc=1").get === Map("xyz:abc" -> "1"))
    assert(getMatrixParams("a=1;b=2").get === Map("a" -> "1", "b" -> "2"))
    assert(getMatrixParams("a=1;b=2;").get === Map("a" -> "1", "b" -> "2"))
    assert(getMatrixParams("a=1;b=2;c=foo").get === Map("a" -> "1", "b" -> "2", "c" -> "foo"))
    assert(getMatrixParams(" a = 1 ; b = 2 ").get === Map("a" -> "1", "b" -> "2"))
  }

  test("Matrix params, invalid cases") {
    intercept[Exception](getMatrixParams("a").get)
    intercept[Exception](getMatrixParams("abc").get)
    intercept[Exception](getMatrixParams("a=").get)
    intercept[Exception](getMatrixParams("a=;").get)
    intercept[Exception](getMatrixParams("=x").get)
    intercept[Exception](getMatrixParams("=x;").get)
    intercept[Exception](getMatrixParams("a=xyz;b").get)
    intercept[Exception](getMatrixParams("a=xyz;b=").get)
    intercept[Exception](getMatrixParams("a=xyz;b=;").get)
  }
}