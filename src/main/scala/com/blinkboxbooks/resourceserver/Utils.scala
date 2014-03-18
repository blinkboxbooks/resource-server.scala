package com.blinkboxbooks.resourceserver

import org.apache.commons.codec.digest.DigestUtils

/**
 * The traditional bag o' stuff that doesn't quite fit in anywhere else.
 */
object Utils {

  /** Return hash of given string in hex format. */
  def stringHash(str: String) = DigestUtils.md5Hex(str)

  /**
   * @returns a pair of (original extension, target extension).
   * The former represents the original extension of the file.
   * The latter represents the target extension in a request for image conversion.
   *
   * @throws IllegalArgumentException if the given file name has no extension at all.
   */
  def fileExtension(filename: String): (Option[String], Option[String]) = filename.lastIndexOf(".") match {
    case -1 => (None, None)
    case pos => (Some(filename.substring(pos + 1, filename.size).toLowerCase), None)
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

}
