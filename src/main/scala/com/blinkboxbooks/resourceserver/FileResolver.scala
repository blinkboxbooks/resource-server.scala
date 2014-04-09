package com.blinkboxbooks.resourceserver

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.LinkOption
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.util.zip.ZipFile
import scala.collection.JavaConverters._
import scala.util.Try
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.io.input.ProxyInputStream
import org.apache.commons.io.IOUtils

trait FileResolver {

  /**
   * Look up requested path. Returns an input stream for the file data,
   * no matter what the underlying storage is.
   */
  def resolve(path: String): Try[InputStream]

}

/**
 * Class that knows how to resolve regular files, as well as files inside epub/Zip files.
 */
class EpubEnabledFileResolver(root: Path) extends FileResolver with Logging {

  import EpubEnabledFileResolver._

  if (!Files.isDirectory(root)) throw new IOException(s"Path must be a valid directory: $root")

  def resolve(path: String) = Try {
    val (epubPath, filePath) = parseEpubPath(path)
    // Look up file in Zip file if it refers to inside an ePub file, otherwise directly.
    epubPath match {
      case None => fromFile(filePath)
      case Some(path) => fromZipFile(path, filePath)
    }
  }

  /** Read file direct from file system. */
  private def fromFile(path: String) = Files.newInputStream(resolvedPath(path))

  /** Read file from inside Zip file. */
  private def fromZipFile(epubPath: String, filePath: String): InputStream = {
    val zipFile = new ZipFile(resolvedPath(epubPath).toFile)
    val entries = zipFile.entries.asScala
    val entry = entries.find(e => e.getName == filePath)
    val inputStream = entry.map(e => zipFile.getInputStream(e))
      .getOrElse(throw new NoSuchFileException(s"No file '$filePath' in file '$epubPath'"))

    // Return a wrapped stream that closes the enclosing Zip file when closed.
    new ProxyInputStream(inputStream) {
      override def close() = {
        IOUtils.closeQuietly(zipFile)
        super.close()
      }
    }
  }

  /** Look up path below root, and check we can't access directories above the root directory. */
  private def resolvedPath(path: String) = {
    if (Paths.get(path).isAbsolute)
      throw new AccessDeniedException(s"Absolute path '$path' not allowed")
    if (Paths.get(path).iterator.asScala.contains(Paths.get("..")))
      throw new AccessDeniedException(s"Relative path '$path' not allowed")

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
