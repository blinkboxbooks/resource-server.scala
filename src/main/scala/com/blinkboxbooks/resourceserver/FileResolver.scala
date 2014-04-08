package com.blinkboxbooks.resourceserver

import scala.util.Try
import java.io.File
import com.typesafe.scalalogging.slf4j.Logging
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.nio.file.FileSystems
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.nio.file.LinkOption
import java.nio.file.FileSystem
import scala.collection.JavaConverters._
import java.net.URI
import java.nio.file.Paths
import java.io.IOException
import java.nio.file.Files

trait FileResolver {

  /**
   * Look up requested path. Returns a path that can be used to read file data,
   * no matter what the underlying storage is.
   */
  def resolve(path: String): Try[Path]

}

/**
 * Class that knows how to resolve regular files, as well as files inside epub files,
 * that is: files with an .epub extension, which are treated as Zip files.
 */
class EpubEnabledFileResolver(root: String) extends FileResolver with Logging {

  import EpubEnabledFileResolver._

  val fs = FileSystems.getDefault
  val rootPath = fs.getPath(root)
  if (!rootPath.toFile.isDirectory()) throw new IOException(s"Path must be a valid directory: $root")

  def resolve(path: String): Try[Path] = Try {
    val (epubPath, filePath) = parseEpubPath(path)

    // Look up file in default file system, or Zip file system if it refers to inside an ePub file.
    val (resolvedFs, baseDirectory) = epubPath match {
      case Some(path) => zipFile(rootPath.toAbsolutePath().toString, path)
      case None => (fs, root)
    }

    val resolvedBase = resolvedFs.getPath(baseDirectory)
    val resolvedPath = resolvedBase.resolve(filePath)

    // Don't allow access to files above the root directory (using ".." in path).
    val absDirPath = resolvedPath.getParent.toRealPath(LinkOption.NOFOLLOW_LINKS)
    if (rootPath.getParent.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(absDirPath))
      throw new AccessDeniedException(s"Access not allowed to path '$path'")

    if (!Files.exists(resolvedPath)) throw new FileNotFoundException(s"File not found at path '$path'")

    resolvedPath
  }

  private def zipFile(rootPath: String, epubPath: String): (FileSystem, String) = {
    val env = Map("create" -> "true").asJava
    val uri = URI.create(s"jar:file:$rootPath/$epubPath");
    (FileSystems.newFileSystem(uri, env), "/")
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
