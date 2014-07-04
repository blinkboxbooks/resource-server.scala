package com.blinkboxbooks.resourceserver

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.duration._

/** Configuration for time logging, with default values. */
trait TimeLoggingThresholds {

  def infoThreshold: Duration = 0.millis
  def warnThreshold: Duration = 250.millis
  def errorThreshold: Duration = 500.millis

}

trait TimeLogging extends Logging with TimeLoggingThresholds {

  val Debug = 0
  val Info = 1
  val Warn = 2
  val Error = 3
  val Auto = 4

  /**
   *  Method that executes a given block of code and logs the execution time.
   *  The return value of this function is the return value of the executed block.
   */
  def time[T](label: String, level: Int = Auto)(func: => T) = {

    val startTime = System.currentTimeMillis
    val res = func
    val time = System.currentTimeMillis - startTime

    (level, time) match {
      case (Debug, t) => logger.debug(message(label, t))
      case (Info, t) => logger.info(message(label, t))
      case (Warn, t) => logger.warn(message(label, t))
      case (Error, t) => logger.error(message(label, t))
      case (Auto, t) if t > errorThreshold.toMillis => logger.error(message(label, t))
      case (Auto, t) if t > warnThreshold.toMillis => logger.warn(message(label, t))
      case (Auto, t) if t > infoThreshold.toMillis => logger.info(message(label, t))
      case _ => // Do nothing.
    }
    res
  }

  private def message(label: String, t: Long) = s"Time for $label: ${t} ms"

}
