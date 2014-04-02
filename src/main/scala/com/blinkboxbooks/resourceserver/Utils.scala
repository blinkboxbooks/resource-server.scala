package com.blinkboxbooks.resourceserver

import org.apache.commons.codec.digest.DigestUtils
import scala.tools.nsc.matching.Patterns
import java.awt.image.BufferedImage
import resource.Resource
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executor

/**
 * The traditional bag o' stuff that doesn't quite fit in anywhere else.
 */
object Utils {

  /** Return hash of given string in hex format. */
  def stringHash(str: String) = DigestUtils.md5Hex(str)

  /** Make BufferedImages managed resources so they can be automatically flushed when no longer used. */
  implicit def pooledConnectionResource[A <: BufferedImage] = new Resource[A] {
    override def close(r: A) = r.flush()
    override def toString = "Resource[java.awt.image.BufferedImage]"
  }

}
