package com.blinkboxbooks.resourceserver

import com.typesafe.scalalogging.slf4j.Logging

trait TimeLogging extends Logging {

  /**
   *  Method that executes a given block of code and logs the execution time.
   *  The return value of this function is the return value of the executed block.
   */
  def time[T](label: String)(func: => T) = {
    val startTime = System.currentTimeMillis
    val res = func
    logger.info(s"Time for $label: ${System.currentTimeMillis - startTime}")
    res
  }

}
