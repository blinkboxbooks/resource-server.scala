package com.blinkboxbooks.resourceserve

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import io.gatling.http.request.builder.AbstractHttpRequestBuilder.toActionBuilder
import scala.Array.canBuildFrom
import java.util.Properties
import java.io.FileInputStream

/**
 * Resource server epub scenarios
 *
 * Gatling 2
 *
 * All HTTP calls must be fully qualified URLS
 * 
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 *
 * https://groups.google.com/forum/#!msg/gatling/N9XAdK-aJ1c/oFzqySmzDNEJ
 */
object EpubScenarios {

  import ScenarioUtils._

  // SETUP ------------------------------------------------------

  // Set to where your resources are:
  val paths = findPaths(filesPath, "epubs", Set("epub")).random

  val outputSizes = Array(99, 150, 153, 167, 330, 362, 366, 731)
  val sizes = outputSizes.zip(Stream.continually("size")).map { case (k, v) => Map(v -> k.toString) }.random
  
  val imageFormats = Array("png","gif")
  val formats = imageFormats.zip(Stream.continually("format")).map { case (k, v) => Map(v -> k.toString) }.random

  // REQUESTS ------------------------------------------------------

  // Absolute path is not currently supported by the Resource server
  val prefix = mediaScheme + "://" + mediaHostname + ":" + mediaPort
    
  val epubImageRequest = exec(
    http("epub image size w=${size} ${format}")
      .get(prefix + "/params;v=0;img:w=${size};img:m=scale/${path}/images/test.${format}.jpg")
      .headers(media_headers)
      .check(status.is(200))
      .check(responseTimeInMillis.lessThan(maxDynamicResponseTimeInMillis)))

  val epubHtmlRequest = exec(
    http("epub html")
      .get(prefix + "/params;v=0;/${path}/content/sub/ch01.html")
      .headers(media_headers)
      .check(status.is(200))
      .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis)))

      val secretKeyRequest = exec(
    http("epub secret key")
      .get(prefix + "/params;v=0;/${path}/secret.key")
      .headers(media_headers)
      .check(status.is(404))
      .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis)))
      
  // SCENARIOS ------------------------------------------------------

  val epubImagesScn = scenario("get pseudo-random sequence of epub images")
    .feed(paths)
    .feed(sizes)
    .feed(formats)
    .exec(group("epub image requests") { epubImageRequest })

  val epubHtmlScn = scenario("get epub html")
    .feed(paths)
    .exec(group("epub static requests") { epubHtmlRequest })

  val epubSecretKeyScn = scenario("get epub secret key")
    .feed(paths)
    .exec(group("epub static requests") { secretKeyRequest })
}
