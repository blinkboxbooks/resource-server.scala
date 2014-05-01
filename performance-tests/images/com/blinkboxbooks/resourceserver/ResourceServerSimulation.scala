package com.blinkboxbooks.resourceserver

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.Properties
import java.io.FileInputStream
import io.gatling.http.config.HttpProxyBuilder.toProxy
import io.gatling.core.controller.inject.InjectionStep

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
class ResourceServerSimulation extends BlinkboxSimulation {

  // SPECIFIC PROPERTIES ------------------------------------------------------

  val imageScenarios = ImageScenarios
  val epubScenarios = EpubScenarios
  
  // HTTP CONFIG ------------------------------------------------------

  val httpConf = httpConfNoProxy
  .baseURL(ScenarioUtils.mediaScheme + "://" + ScenarioUtils.mediaHostname + ":" + ScenarioUtils.mediaPort)

  // TEST PLAN ------------------------------------------------------

  setUp(

    imageScenarios.imageResizingScn.inject(
      fullRampUpRate,fullConstantRate)
      .protocols(httpConf),

    imageScenarios.imageNotFoundScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    imageScenarios.imageQualityScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    imageScenarios.invalidImages1Scn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    imageScenarios.invalidImages2Scn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    imageScenarios.invalidVersionImageScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    imageScenarios.gravityImageScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    epubScenarios.epubImagesScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    epubScenarios.epubHtmlScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf),

    epubScenarios.epubSecretKeyScn.inject(
      _10thRampUpRate,_10thConstantRate)
      .protocols(httpConf)
      
    )
  
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
