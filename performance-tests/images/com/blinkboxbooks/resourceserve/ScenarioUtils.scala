package com.blinkboxbooks.resourceserve

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.io.File
import scala.collection.immutable.Stream.consWrapper

/**
 * Resource server gatling utils
 *
 * Gatling 2
 *
 * https://github.com/excilys/gatling/wiki/Advanced-Usage#modularization
 */
class ScenarioUtils {

  
    
  // Helper functions.
    
  def findPaths(root: String, extensions: Set[String]): Array[Map[String, String]] =
    findFiles(new File(root))
      .filter(matchesExtension(_, extensions))
      .map(f => Map("path" -> f.getPath.drop(root.length))) //.drop(root.length)
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


}