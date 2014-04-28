package com.blinkboxbooks.resourceserve

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import io.gatling.http.request.builder.AbstractHttpRequestBuilder.toActionBuilder
import scala.Array.canBuildFrom
import java.util.Properties
import java.io.FileInputStream

/**
 * <pre>
 * Resource server image scenarios
 *
 * Gatling 2
 *
 * All HTTP calls must be fully qualified URLS
 * 
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 *
 * https://groups.google.com/forum/#!msg/gatling/N9XAdK-aJ1c/oFzqySmzDNEJ
 *
 * -----------------------------------------
 *
 * TEST SET UP :
 *
 * see the README.md in the images directory.
 *
 * </pre>
 */
object ImageScenarios {

  import ScenarioUtils._

  // SETUP ------------------------------------------------------

  val paths = findPaths(filesPath, Set("png", "jpg", "jpeg")).random
  //val paths = Array(Map("path" -> "9780/709/092/599/7fca309750b4593280ebf85db9a080a9.png")).random

  val outputSizes = Array(99, 150, 153, 167, 330, 362, 366, 731)
  val sizes = outputSizes.zip(Stream.continually("size")).map { case (k, v) => Map(v -> k.toString) }.random

  val outputQualities = Array(50, 75, 80, 85)
  val qualities = outputQualities.zip(Stream.continually("quality")).map { case (k, v) => Map(v -> k.toString) }.random

  /**
   * md5's for different sizes of big-NNNNN.png files
   */
  val expectedHashes = Map(
    // for some reason Int sizes can't be found when later querying the map...
    ("150" -> "85dd7ccdeaa7b3ff8a14a5b19193498d"),
    ("153" -> "2e7a7c9df94a5d6a0e16de99b44bddbd"),
    ("167" -> "99a3dc3191712eb7e3bbdbde5acc2592"),
    ("330" -> "2e4d6ef3bb6676ce80a8bf579ee15ff0"),
    ("362" -> "ea1a879b6cda0456b1cbd1df061f0ff2"),
    ("366" -> "dbad9f1c107743c535a0e1cf9e69fb0e"),
    ("731" -> "54fd3ed57a00f9c14a1c8aff5a0863e9"),
    ("99" -> "93f79354fa351591f51df60ef6262a24"))

  // REQUESTS ------------------------------------------------------

  val get_md5 = exec((s: Session) => {
    val expectedMD5 = expectedHashes(s("size").as[String])
    s.set("expectedMD5", expectedMD5)
  })

  // Absolute path is not currently supported by the Resource server
  val prefix = mediaScheme + "://" + mediaHostname + ":" + mediaPort
  
  val resizeImageRequest = exec(
    http("file image size w=${size}")
      .get(prefix + "/params;img:w=${size};img:m=scale;v=0/${path}.jpg")
      .headers(media_headers)
      .check(status.is(200))
      // uncomment this if you are testing with the big-NNNN.png files
      //.check(md5.is("${expectedMD5}"))
      .check(responseTimeInMillis.lessThan(maxDynamicResponseTimeInMillis)))

  val notfoundImageRequest = exec(
    http("file image not found")
      .get(prefix + "/params;img:w=${size};img:m=scale;v=0/doesnotexist.jpg")
      .headers(media_headers)
      .check(status.is(404))
      .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis)))

  val qualityImageRequest = exec(
    http("file image low quality")
      .get(prefix + "/params;img:w=${size};img:m=scale;img:q=${quality};v=0/${path}.jpg")
      .headers(media_headers)
      .check(status.is(200))
      .check(responseTimeInMillis.lessThan(maxDynamicResponseTimeInMillis)))

  val invalidImageRequest = exec(
    http("file image invalid1")
      .get(prefix + "/params;img:w=-1;img:m=scale;img:q=-1;v=0/${path}.jpg?")
      .headers(media_headers)
      .check(status.is(400))
      .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis)))

  val invalidImageRequest2 = exec(
    http("file image invalid2")
      .get(prefix + "/../../../../../../../../../../../../etc/hosts")
      .headers(media_headers)
      .check(status.is(400))
      .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis)))

  //  should return response code 400 & "Server version #NN is not yet specified."
  val invalidVersionImageRequest = exec(
    http("file image invalid version")
      .get(prefix + "/params;img:w=1;img:m=scale;img:q=99;v=99/${path}.jpg")
      .headers(media_headers)
      .check(status.is(400))
      .check(regex("Server version #99 is not yet specified."))
      .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis)))

  // gravity img:m=crop , img:g=nwsec|ne|we
  val gravityImageRequest = exec(
    http("file image gravity")
      .get(prefix + "/params;img:w=200;img:m=scale;img:q=85;v=0;img:m=crop;img:g=n/${path}.jpg")
      .headers(media_headers)
      .check(status.is(200))
      .check(responseTimeInMillis.lessThan(maxDynamicResponseTimeInMillis)))

  // SCENARIOS ------------------------------------------------------

  val imageResizingScn = scenario("pseudo-random sequence of file images")
    .feed(paths)
    .feed(sizes)
    .exec(group("processed images") {
      // uncomment this if you are testing with the big-NNNN.png files to check the MD5 of the response...
      //get_md5,
      resizeImageRequest
    })

  val imageNotFoundScn = scenario("not found file images")
    .feed(sizes)
    .exec(group("invalid images") { notfoundImageRequest })

  val imageQualityScn = scenario("reduced quality file images")
    .feed(paths)
    .feed(sizes)
    .feed(qualities)
    .exec(group("processed images") { qualityImageRequest })

  val invalidImages1Scn = scenario("invalid1 file images")
    .feed(paths)
    .feed(sizes)
    .exec(group("invalid images") { invalidImageRequest })

  val invalidImages2Scn = scenario("invalid2 file images")
    .feed(paths)
    .feed(sizes)
    .exec(group("invalid images") { invalidImageRequest2 })

  val invalidVersionImageScn = scenario("invalid version file images")
    .feed(paths)
    .feed(sizes)
    .exec(group("invalid images") { invalidVersionImageRequest })

  val gravityImageScn = scenario("gravity file images")
    .feed(paths)
    .feed(sizes)
    .exec(group("processed images") { gravityImageRequest })
}