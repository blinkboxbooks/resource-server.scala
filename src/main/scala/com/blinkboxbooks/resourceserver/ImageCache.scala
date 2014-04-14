package com.blinkboxbooks.resourceserver

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardOpenOption._
import java.nio.file.StandardCopyOption._
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream
import javax.imageio.stream.ImageOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import java.awt.image.BufferedImage
import org.imgscalr.Scalr
import org.imgscalr.Scalr.Method
import org.imgscalr.Scalr.Mode
import com.typesafe.scalalogging.slf4j.Logging
import resource._
import Utils._
import scala.util.Try
import scala.util.control.NonFatal
import java.nio.file.CopyOption

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
   * @returns the smallest image that satisfies this constraint.
   */
  def getImage(path: String, minSize: Int): Option[InputStream]

  /**
   * @returns true if the given file size is smaller than the maximum size
   * of images stored in the cache. Always returns false for an undefined size.
   */
  def wouldCacheImage(size: Option[Int]): Boolean
}

class FileSystemImageCache(root: Path, sizes: Set[Int], resolver: FileResolver) extends ImageCache with Logging {

  val TEMP_EXTENSION = ".work"

  // Ordered list of the sizes at which images are cached.
  val targetSizes = sizes.toList.sorted

  override def addImage(path: String) {
    logger.debug(s"Caching image at $path")
    val original = resolver.resolve(path).get
    for (
      input <- managed(original);
      image <- managed(ImageIO.read(input))
    ) {
      if (image == null) throw new IOException(s"Unable to decode image at $path")
      for (
        size <- targetSizes;
        resized <- managed(Scalr.resize(image, Method.BALANCED, Mode.AUTOMATIC, size))
      ) {
        val outputPath = cachedFilePath(path, size)
        val tmpOutputFile = root.resolve(outputPath + TEMP_EXTENSION)

        Files.createDirectories(tmpOutputFile.getParent())
        try {
          for (tmpOutput <- managed(Files.newOutputStream(tmpOutputFile, CREATE, TRUNCATE_EXISTING))) {
            // Write file to temporary file, then do an atomic update when ready.
            // This ensures that threads looking for cached files don't get a partially written file.
            writeFile(resized, tmpOutput)
            val outputFile = root.resolve(outputPath)
            Files.move(tmpOutputFile, outputFile, ATOMIC_MOVE, REPLACE_EXISTING)
            logger.debug("Wrote output file: " + outputFile + " with dimensions (" + resized.getWidth() + "[w], "
              + resized.getHeight() + "[h])")
          }
        } catch {
          case NonFatal(e) =>
            if (Files.exists(tmpOutputFile)) Files.delete(tmpOutputFile)
            throw e
        }
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

  private def cachedFilePath(path: String, size: Int) = s"${size}x${size}" + File.separator + path

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

