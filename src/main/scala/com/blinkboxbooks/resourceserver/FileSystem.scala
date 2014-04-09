package com.blinkboxbooks.resourceserver

import java.nio.file.Path
import java.io.File
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.apache.commons.vfs2.provider.zip.ZipFileProvider
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider
import org.apache.commons.vfs2.impl.DefaultFileReplicator
import org.apache.commons.vfs2.cache.SoftRefFilesCache

object FileSystem {

  /**
   * Create a file system manager that resolves paths in ePub and Zip files,
   * as well as regular files.
   */
  def createZipFileSystem(rootDirectory: Path, tmpDir: Option[File]) = {
    val fsManager = new DefaultFileSystemManager()
    fsManager.addProvider(Array("zip"), new ZipFileProvider())
    fsManager.addProvider(Array("file"), new DefaultLocalFileProvider())
    fsManager.setFilesCache(new SoftRefFilesCache())
    tmpDir.foreach { dir => fsManager.setTemporaryFileStore(new DefaultFileReplicator(dir)) }
    fsManager.init()
    fsManager.setBaseFile(rootDirectory.toFile)
    fsManager
  }

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