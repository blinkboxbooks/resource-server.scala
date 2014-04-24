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
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 * 
 * https://groups.google.com/forum/#!msg/gatling/N9XAdK-aJ1c/oFzqySmzDNEJ
 */
object EpubScenarios extends ScenarioUtils {

    // SETUP ------------------------------------------------------
  
      val prop = new Properties()
      prop.load(new FileInputStream("./ResourceServerScenarios.properties"))
      val filesPath = prop.getProperty("filesPath")
      val maxDynamicResponseTimeInMillis1 = prop.getProperty("maxDynamicResponseTimeInMillis")
      val maxDynamicResponseTimeInMillis = maxDynamicResponseTimeInMillis1.toLong
      val maxStaticResponseTimeInMillis1 = prop.getProperty("maxStaticResponseTimeInMillis")
      val maxStaticResponseTimeInMillis = maxStaticResponseTimeInMillis1.toLong
      
  // Set to where your resources are:
  val paths = findPaths(filesPath, "epubs", Set("epub")).random
  
  val outputSizes = Array(99, 150, 153, 167, 330, 362, 366, 731)
  val sizes = outputSizes.zip(Stream.continually("size")).map{ case (k, v) => Map(v -> k.toString) }.random

    // REQUESTS ------------------------------------------------------
  
  val epubImageRequest = exec(
          http("epub image size w=${size}")
            .get("/params;v=0;img:w=${size};img:m=scale/${path}/OEBPS/Images/logo.jpg")
            .check(status.is(200))
            .check(responseTimeInMillis.lessThan(maxDynamicResponseTimeInMillis))
  )
            
            
            
  val epubHtmlRequest = exec(
          http("epub html")
            .get("/params;v=0;/${path}/OEBPS/022_chapter17.html")
            .check(status.is(200))
            .check(responseTimeInMillis.lessThan(maxStaticResponseTimeInMillis))
  )
            
  
    // SCENARIOS ------------------------------------------------------
  
  val epubImagesScn = scenario("get pseudo-random sequence of epub images")
      .feed(paths)
        .feed(sizes)
        .exec(group("epub image requests"){epubImageRequest})

  val epubHtmlScn = scenario("get epub html")
      .feed(paths)
        .feed(sizes)
        .exec(group("epub static requests"){epubHtmlRequest})

}
