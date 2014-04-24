package com.blinkboxbooks.resourceserve

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import java.util.Properties
import java.io.FileInputStream
import io.gatling.http.config.HttpProxyBuilder.toProxy

/**
 * Resource server simulation
 *
 * Gatling 2
 *
 * the simulation contains the TEST PLAN - ie. duration, throughput etc.
 *
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 *
 * https://github.com/excilys/gatling/wiki/Gatling-2#assertions
 * https://github.com/excilys/gatling/wiki/Assertions
 *
 */
class ResourceServerSimulation extends Simulation {

  // PROPERTIES ------------------------------------------------------

  val prop = new Properties()
  prop.load(new FileInputStream("./ResourceServerSimulation.properties"))

  val usersPerSec = prop.getProperty("usersPerSec").toDouble
  val rampSeconds = new Integer(prop.getProperty("rampSeconds"))
  val constantLoadSeconds = new Integer(prop.getProperty("constantLoadSeconds"))
  val scheme = prop.getProperty("scheme")
  val hostname = prop.getProperty("hostname")
  val port = prop.getProperty("port")

  val successfulRequestsPercent = prop.getProperty("successfulRequestsPercent")

  // NOTE: assumes percentile1 and percentile2 is set to 50 and 90 in gatling.conf
  
  val medianDynamicResponseTimeInMillis1 = prop.getProperty("medianDynamicResponseTimeInMillis")
  val medianDynamicResponseTimeInMillis = medianDynamicResponseTimeInMillis1.toInt
  val medianStaticResponseTimeInMillis1 = prop.getProperty("medianStaticResponseTimeInMillis")
  val medianStaticResponseTimeInMillis = medianStaticResponseTimeInMillis1.toInt

  val _90THDynamicResponseTimeInMillis1 = prop.getProperty("_90THDynamicResponseTimeInMillis")
  val _90THDynamicResponseTimeInMillis = _90THDynamicResponseTimeInMillis1.toInt
  val _90THStaticResponseTimeInMillis1 = prop.getProperty("_90THStaticResponseTimeInMillis")
  val _90THStaticResponseTimeInMillis = _90THStaticResponseTimeInMillis1.toInt

  val maxDynamicResponseTimeInMillis1 = prop.getProperty("maxDynamicResponseTimeInMillis")
  val maxDynamicResponseTimeInMillis = maxDynamicResponseTimeInMillis1.toInt
  val maxStaticResponseTimeInMillis1 = prop.getProperty("maxStaticResponseTimeInMillis")
  val maxStaticResponseTimeInMillis = maxStaticResponseTimeInMillis1.toInt

  // HTTP CONFIG ------------------------------------------------------

  val httpConfNoProxy = http
    .baseURL(scheme + "://" + hostname + ":" + port) //"http://localhost:8080")
    .acceptCharsetHeader("utf-8")
    .acceptHeader("application/vnd.blinkboxbooks.data.v1+json")
    .acceptEncodingHeader("gzip, deflate")

  val httpConfProxy = httpConfNoProxy
    .proxy(Proxy("localhost", 8000).httpsPort(8001))

  val httpConf = httpConfNoProxy

  // TEST PLAN ------------------------------------------------------

  val imageScenarios = ImageScenarios
  val epubScenarios = EpubScenarios

  setUp(

    imageScenarios.imageResizingScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    imageScenarios.imageNotFoundScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec / 10) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec / 10).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    imageScenarios.imageQualityScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec / 10) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec / 10).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    imageScenarios.invalidImages1Scn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec / 10) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec / 10).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    imageScenarios.invalidImages2Scn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec / 10) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec / 10).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    imageScenarios.invalidVersionImageScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec / 10) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec / 10).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    imageScenarios.gravityImageScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec / 10) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec / 10).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    epubScenarios.epubImagesScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec).during(constantLoadSeconds seconds))
      .protocols(httpConf),

    epubScenarios.epubHtmlScn.inject(
      // constantUsersPerSec(0.5).during(60 seconds)
      rampUsersPerSec(1) to (usersPerSec) during (rampSeconds seconds),
      constantUsersPerSec(usersPerSec).during(constantLoadSeconds seconds))
      .protocols(httpConf))

    // TEST ASSERTIONS -------------------------------------------
    // chained outside of setup() in gatling 2

    .assertions(

      // global stuff
      global.responseTime.max.lessThan(maxDynamicResponseTimeInMillis),
      global.successfulRequests.percent.is(successfulRequestsPercent.toInt),

      // per group 
      details("processed images").responseTime.percentile1.lessThan(medianDynamicResponseTimeInMillis),
      details("processed images").responseTime.percentile2.lessThan(_90THDynamicResponseTimeInMillis),
      details("processed images").responseTime.max.lessThan(maxDynamicResponseTimeInMillis),

      // "sustained throughput" requirement
      // TODO - doesn't work yet
      //details("resized images" ).requestsPerSec.assert(
      //  value => value > usersPerSec * 0.95,
      //  (name, value) => name + " has NOT met sustained throughput of " + usersPerSec * 0.95),

      details("invalid images").responseTime.percentile1.lessThan(medianStaticResponseTimeInMillis),
      details("invalid images").responseTime.percentile2.lessThan(_90THStaticResponseTimeInMillis),
      details("invalid images").responseTime.max.lessThan(maxStaticResponseTimeInMillis),

      details("epub image requests").responseTime.percentile1.lessThan(medianDynamicResponseTimeInMillis),
      details("epub image requests").responseTime.percentile2.lessThan(_90THDynamicResponseTimeInMillis),
      details("epub image requests").responseTime.max.lessThan(maxDynamicResponseTimeInMillis),

      details("epub static requests").responseTime.percentile1.lessThan(medianStaticResponseTimeInMillis),
      details("epub static requests").responseTime.percentile2.lessThan(_90THStaticResponseTimeInMillis),
      details("epub static requests").responseTime.max.lessThan(maxStaticResponseTimeInMillis))
  // END TEST ASSERTIONS -------------------------------------------
}
