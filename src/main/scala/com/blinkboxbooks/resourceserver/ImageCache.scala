package com.blinkboxbooks.resourceserver

import java.awt.image.BufferedImage
import java.io.{File, IOException, InputStream, OutputStream}
import java.nio.file.{Files, Path}
import java.nio.file.StandardCopyOption._
import java.nio.file.StandardOpenOption._
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream

import com.blinkboxbooks.resourceserver.Utils._
import com.typesafe.scalalogging.StrictLogging
import org.imgscalr.Scalr
import org.imgscalr.Scalr.{Method, Mode}
import resource._

import scala.util.Try
import scala.util.control.NonFatal

/**
 * A specialised cache that returns cached image files of various sizes.
 *
 */
trait ImageCache {

  /**
   * Add a new image to the cache, this will cause images of various sizes to be
   * generated within the cache.
   *
   * @throws IOException if unable to store image files.
   */
  def addImage(path: String)

  /**
   * Look for a file with a cached image where the bounding size of the cached image
   * is at least the given size.
   * @return the smallest image that satisfies this constraint.
   */
  def getImage(path: String, minSize: Int): Option[InputStream]

  /**
   * @return true if the given file size is smaller than the maximum size
   * of images stored in the cache. Always returns false for an undefined size.
   */
  def wouldCacheImage(size: Option[Int]): Boolean
}

class FileSystemImageCache(root: Path, sizes: Set[Int], resolver: FileResolver, writingEnabled: Boolean)
  extends ImageCache with StrictLogging {

  // Ordered list of the sizes at which images are cached.
  val targetSizes = sizes.toList.sorted

  override def addImage(path: String) {
    if (!writingEnabled) {
      logger.debug("Caching disabled, ignoring")
      return
    }
    
    logger.debug(s"Caching image at $path")
    for (
      input <- managed(resolver.resolve(path).get);
      originalImage <- managed(ImageIO.read(input))
    ) {
      if (originalImage == null) throw new IOException(s"Unable to decode image at $path")

      // Generate images at each of intermediate sizes that we keep.
      for (
        size <- targetSizes;
        resizedImage <- managed(Scalr.resize(originalImage, Method.BALANCED, Mode.AUTOMATIC, size))
      ) {
        val outputPath = cachedFilePath(path, size)
        safelyWriteFile(resizedImage, outputPath)
      }
    }
  }

  /** @return the first image with a size bigger than the minimum size, or None if no such exists. */
  override def getImage(path: String, minSize: Int): Option[InputStream] =
    for (
      suitableCachedSize <- targetSizes.find(_ >= minSize);
      cachedPath <- Some(cachedFilePath(path, suitableCachedSize));
      file <- Try(root.resolve(cachedPath)).toOption if (Files.exists(file))
    ) yield Files.newInputStream(file)

  /** Check whether this cache is configured with any image sizes that are within the given size. */
  override def wouldCacheImage(size: Option[Int]) = size.isDefined && size.get <= targetSizes.max

  /** Returns the path where an image of a given size would be stored. */
  private def cachedFilePath(path: String, size: Int) = s"${size}x${size}" + File.separator + path

  /**
   * Atomically write an image file to disk, creating any necessary parent directories.
   * Ensures that no partially file is left behind if an exception happens part way through writing.
   */
  private def safelyWriteFile(image: BufferedImage, outputPath: String) = {
    // Write file to temporary file, then do an atomic update when ready.
    // This ensures that threads looking for cached files don't get a partially written file.
    val tmpOutputFile = root.resolve(outputPath + ".work")
    Files.createDirectories(tmpOutputFile.getParent())
    try {
      for (tmpOutput <- managed(Files.newOutputStream(tmpOutputFile, CREATE, TRUNCATE_EXISTING))) {
        writeFile(image, tmpOutput)
        val outputFile = root.resolve(outputPath)
        Files.move(tmpOutputFile, outputFile, ATOMIC_MOVE, REPLACE_EXISTING)
        logger.debug("Wrote image file: " + outputFile + " with dimensions (" + image.getWidth() + "[w], "
          + image.getHeight() + "[h])")
      }
    } catch {
      case NonFatal(e) =>
        if (Files.exists(tmpOutputFile)) Files.delete(tmpOutputFile)
        throw e
    }
  }

  private def writeFile(image: BufferedImage, output: OutputStream) {
    for (
      writer <- managed(ImageIO.getImageWritersByFormatName("png").next());
      imageOutputStream <- managed(new MemoryCacheImageOutputStream(output))
    ) {
      writer.setOutput(imageOutputStream)
      writer.write(image)
    }
  }

}

