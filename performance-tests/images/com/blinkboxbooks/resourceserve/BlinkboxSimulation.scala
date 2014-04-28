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
 * Abstract super class for all simulations
 *
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 *
 * https://github.com/excilys/gatling/wiki/Gatling-2#assertions
 * https://github.com/excilys/gatling/wiki/Assertions
 *
 */
class BlinkboxSimulation extends Simulation {

  // we have to import this for the base URL which will be www
  import ScenarioUtils._

  // PROPERTIES ------------------------------------------------------

  val prop = new Properties()
  prop.load(new FileInputStream("./ResourceServerSimulation.properties"))

  val usersPerSec = prop.getProperty("usersPerSec").toDouble
  val rampSeconds = new Integer(prop.getProperty("rampSeconds"))
  val constantLoadSeconds = new Integer(prop.getProperty("constantLoadSeconds"))

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
    // in the general case www will be the base url and ALL other http request urls must be fully qualified in each request
    .baseURL(wwwScheme + "://" + wwwHostname + ":" + wwwPort)
    // "http://localhost:8080")
    .acceptCharsetHeader("utf-8")
    .acceptLanguageHeader("""en-US,en;q=0.8""")
    .acceptEncodingHeader("gzip, deflate")

  val httpConfProxy = httpConfNoProxy
    .proxy(Proxy("localhost", 8000).httpsPort(8001))

    // Test plan ------------------------------------------------------
    
  val fullRampUpRate = rampUsersPerSec(0.1) to (usersPerSec) during (rampSeconds seconds)
  val fullConstantRate = constantUsersPerSec(usersPerSec).during(constantLoadSeconds seconds)
  
  val _10thRampUpRate = rampUsersPerSec(0.1) to (usersPerSec/10) during (rampSeconds seconds)
  val _10thConstantRate = constantUsersPerSec(usersPerSec/10).during(constantLoadSeconds seconds)
  
  
}
