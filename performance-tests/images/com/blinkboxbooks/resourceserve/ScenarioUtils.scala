package com.blinkboxbooks.resourceserve

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.io.File
import scala.collection.immutable.Stream.consWrapper
import java.util.Properties
import java.io.FileInputStream

/**
 * Resource server gatling utils
 *
 * Gatling 2
 *
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 */
object ScenarioUtils {

  // Common properties

  val prop = new Properties()
  prop.load(new FileInputStream("./ResourceServerScenarios.properties"))

  val filesPath = prop.getProperty("filesPath")
  val maxDynamicResponseTimeInMillis1 = prop.getProperty("maxDynamicResponseTimeInMillis")
  val maxDynamicResponseTimeInMillis = maxDynamicResponseTimeInMillis1.toLong
  val maxStaticResponseTimeInMillis1 = prop.getProperty("maxStaticResponseTimeInMillis")
  val maxStaticResponseTimeInMillis = maxStaticResponseTimeInMillis1.toLong

  // Helper functions.

  def findPaths(root: String, extensions: Set[String]): Array[Map[String, String]] =
    findFiles(new File(root))
      .filter(matchesExtension(_, extensions))
      .map(f => Map("path" -> f.getPath.drop(root.length)))
      .toArray

  def findFiles(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(findFiles) else Stream.empty)

  def matchesExtension(file: File, extensions: Set[String]) =
    extensions.exists(ext => file.getName.toLowerCase.contains("." + ext))

  def findPaths(root: String, path: String, extensions: Set[String]): Array[Map[String, String]] =
    findFiles(new File(root + path))
      .filter(matchesExtension(_, extensions))
      .map(f => Map("path" -> f.getPath.drop(root.length)))
      .toArray

  // HTTP Config -  for each domain

  val apiScheme = prop.getProperty("apiScheme")
  val apiHostname = prop.getProperty("apiHostname")
  val apiPort = prop.getProperty("apiPort")

  val wwwScheme = prop.getProperty("wwwScheme")
  val wwwHostname = prop.getProperty("wwwHostname")
  val wwwPort = prop.getProperty("wwwPort")

  val authScheme = prop.getProperty("authScheme")
  val authHostname = prop.getProperty("authHostname")
  val authPort = prop.getProperty("authPort")

  val mediaScheme = prop.getProperty("mediaScheme")
  val mediaHostname = prop.getProperty("mediaHostname")
  val mediaPort = prop.getProperty("mediaPort")

  // headers help
  // TODO - improve - or rather fix in the app as looks like various inconsistent request headers sent currently

  val api_headers = Map(
    """Accept""" -> """application/vnd.blinkboxbooks.data.v1+json""",
    """Cache-Control""" -> """no-cache""",
    """Expires""" -> """0""",
    """Pragma""" -> """no-cache""",
    """X-Requested-By""" -> """blinkbox""")

  val www_headers = Map(
    """Accept""" -> """*/*""",
    """Cache-Control""" -> """no-cache""",
    """Pragma""" -> """no-cache""")

  val auth_headers = Map(
    """Accept""" -> """application/json, text/plain, */*""",
    """Cache-Control""" -> """no-cache""",
    """Expires""" -> """0""",
    """Pragma""" -> """no-cache""",
    """X-Requested-By""" -> """blinkbox""",
    """X-Requested-With""" -> """XMLHttpRequest""")

  val media_headers = Map(
    """Accept""" -> """*/*""", // application/json, text/plain,  ........
    """Cache-Control""" -> """no-cache""",
    """Expires""" -> """0""",
    """Pragma""" -> """no-cache""",
    """X-Requested-By""" -> """blinkbox""",
    """Origin""" -> (wwwScheme + "://" + wwwHostname),
    """X-Requested-With""" -> """XMLHttpRequest""")

}