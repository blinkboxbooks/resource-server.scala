package com.blinkboxbooks.resourceserver

import java.io.File
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.FileSystems
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import scala.collection.JavaConverters._
import scala.util.Try
import java.util.zip.ZipFile
import com.typesafe.scalalogging.slf4j.Logging
import java.nio.file.LinkOption
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

trait FileResolver {

  /**
   * Look up requested path. Returns an input stream for the file data,
   * no matter what the underlying storage is.
   */
  def resolve(path: String): Try[InputStream]

}

/**
 * Class that knows how to resolve regular files, as well as files inside epub files,
 * that is: files with an .epub extension, which are treated as Zip files.
 */
class EpubEnabledFileResolver(root: Path) extends FileResolver with Logging {

  import EpubEnabledFileResolver._

  if (!Files.isDirectory(root)) throw new IOException(s"Path must be a valid directory: $root")

  def resolve(path: String) = Try {
    val (epubPath, filePath) = parseEpubPath(path)

    // Look up file in directly, or in Zip file if it refers to inside an ePub file.
    epubPath match {
      case None => fromFile(filePath)
      case Some(path) => fromZipFile(path, filePath)
    }
  }

  private def fromFile(path: String) = Files.newInputStream(resolvedPath(path))

  private def fromZipFile(epubPath: String, filePath: String): InputStream = {
    val zipFile = new ZipFile(resolvedPath(epubPath).toFile)
    val entries = zipFile.entries.asScala
    val entry = entries.find(e => e.getName == filePath)
    entry.map(e => zipFile.getInputStream(e))
      .getOrElse(throw new NoSuchFileException(s"No file '$filePath' in file '$epubPath'"))
  }

  /** Look up path and check we can't access directories above the root. */
  private def resolvedPath(path: String): Path = {
    val resolved = root.resolve(path)
    val absDirPath = resolved.getParent.toRealPath(LinkOption.NOFOLLOW_LINKS)
    if (root.getParent.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(absDirPath))
      throw new AccessDeniedException(s"Access not allowed to path '$path'")
    resolved
  }
}

object EpubEnabledFileResolver {

  /**
   * Given a string path, check for presence of an epub file in any part of it except the last part.
   * If present, return a Path for the epub file and a Path for the file within it.
   * Otherwise, return None for the epub path, and a Path for the file using the whole path.
   *
   * @throw IOException for syntactically invalid paths.
   */
  def parseEpubPath(path: String): (Option[String], String) = {
    val names = path.split("/").toList
    names.span(!_.toLowerCase.endsWith(".epub")) match {
      case (names, Nil) => (None, path)
      case (names, epubFile :: Nil) => (None, path)
      case (names, epubFile :: epubPath) => (Some((names :+ epubFile).mkString("/")), epubPath.mkString("/"))
      case _ => throw new IllegalArgumentException(s"Failed to match names: $names")
    }
  }

}
