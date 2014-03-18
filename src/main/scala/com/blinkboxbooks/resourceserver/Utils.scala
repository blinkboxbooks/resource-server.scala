package com.blinkboxbooks.resourceserver

import org.apache.commons.codec.digest.DigestUtils
import scala.tools.nsc.matching.Patterns
import java.awt.image.BufferedImage
import resource.Resource

/**
 * The traditional bag o' stuff that doesn't quite fit in anywhere else.
 */
object Utils {

  /** Return hash of given string in hex format. */
  def stringHash(str: String) = DigestUtils.md5Hex(str)

  /**
   * @returns a pair of (original extension, target extension).
   * The former contains the extension of the file (in lower case), if present, otherwise None.
   * The latter contains the target extension (in lower case) in a request for image conversion, if requested, otherwise None.
   */
  def fileExtension(filename: String): (Option[String], Option[String]) = {
    filename.split("\\.").reverse.toList match {
      case ext2 :: ext1 :: filenamePart :: _ if acceptedFormat(ext1) && producableFormat(ext2) => (Some(ext1.toLowerCase), Some(ext2.toLowerCase))
      case ext :: filenamePart :: _ => (Some(ext.toLowerCase), None)
      case _ => (None, None)
    }
  }

  /** Make BufferedImages managed resources so they can be automatically flushed when no longer used. */
  implicit def pooledConnectionResource[A <: BufferedImage] = new Resource[A] {
    override def close(r: A) = r.flush()
    override def toString = "Resource[java.awt.image.BufferedImage]"
  }

  /**
   * @return the given path with container files (epubs, zips) referred to using
   * VFS syntax, by appending a "!". E.g. "dir/foo.epub/some/file.html" => "zip:dir/foo.epub!/some/file.html".
   */
  def getVfsPath(filename: String) = {
    // Add exclamation mark after .epub or .zip, except at the end of the path.
    val updated = """(?i)(\.epub|\.zip)/""".r.replaceAllIn(filename, """$1!/""")
    // Add 'zip:' prefix if the path contains at least one archive file.
    if (updated == filename) filename else "zip:" + updated
  }

  def acceptedFormat(extension: String) = ACCEPTED_IMAGE_FORMATS.contains(extension.toLowerCase)
  def producableFormat(extension: String) = PRODUCABLE_IMAGE_FORMATS.contains(extension.toLowerCase)

  private val ACCEPTED_IMAGE_FORMATS = Set("png", "jpg", "jpeg", "gif", "svg", "tif", "tiff", "bmp")
  private val PRODUCABLE_IMAGE_FORMATS = Set("png", "jpg", "jpeg", "gif")

}
