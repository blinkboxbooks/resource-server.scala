package com.blinkboxbooks.resourceserver

import java.io.File
import java.io.OutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream
import javax.imageio.stream.ImageOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import java.awt.image.BufferedImage
import org.imgscalr.Scalr
import org.imgscalr.Scalr.Method
import org.imgscalr.Scalr.Mode
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider
import org.apache.commons.vfs2.cache.SoftRefFilesCache
import com.typesafe.scalalogging.slf4j.Logging
import java.io.IOException
import resource._
import Utils._
import scala.util.Try
import org.apache.commons.vfs2.FileSystemManager
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
  def addImage(path: String, content: FileObject)

  /**
   * Look for a file with a cached image where the bounding size of the cached image
   * is at least the given size.
   * @returns the smallest image that satisfies this constraint.
   */
  def getImage(path: String, minSize: Int): Option[FileObject]

  /**
   * @returns true if the given file size is smaller than the maximum size
   * of images stored in the cache. Always returns false for an undefined size.
   */
  def wouldCacheImage(size: Option[Int]): Boolean
}

class FileSystemImageCache(root: File, sizes: Set[Int], fs: FileSystemManager) extends ImageCache with Logging {

  // Ordered list of the sizes at which images are cached.
  val targetSizes = sizes.toList.sorted

  override def addImage(path: String, content: FileObject) {
    logger.debug(s"Caching image at $path")
    for (
      input <- managed(content.getContent().getInputStream());
      image <- managed(ImageIO.read(input))
    ) {
      if (image == null) throw new IOException(s"Unable to decode image at $path")
      for (
        size <- targetSizes;
        resized <- managed(Scalr.resize(image, Method.BALANCED, Mode.AUTOMATIC, size));
        val outputPath = cachedFilePath(path, size);
        outputFile <- managed(fs.resolveFile(outputPath))
      ) {
        if (outputFile.exists) {
          logger.debug(s"Deleting existing file: $outputFile")
          outputFile.delete()
        }
        outputFile.createFile()
        try {
          for (output <- managed(outputFile.getContent.getOutputStream())) {
            writeFile(resized, output)
            logger.debug("Wrote output file: " + outputFile + " with dimensions (" + resized.getWidth() + "[w], "
              + resized.getHeight() + "[h])")
          }
        } catch {
          case NonFatal(e) =>
            outputFile.delete()
            throw e
        }
      }
    }
  }

  override def getImage(path: String, minSize: Int): Option[FileObject] = {
    for (
      suitableCachedSize <- targetSizes.find(_ >= minSize);
      cachedPath <- Some(cachedFilePath(path, suitableCachedSize));
      file <- Try(fs.resolveFile(cachedPath)).toOption if (file.exists)
    ) yield file
  }

  override def wouldCacheImage(size: Option[Int]) = size.isDefined && size.get <= targetSizes.max

  private def writeFile(image: BufferedImage, output: OutputStream) {
    val writer = ImageIO.getImageWritersByFormatName("png").next()
    for (imageOutputStream <- managed(new MemoryCacheImageOutputStream(output))) {
      writer.setOutput(imageOutputStream)
      writer.write(image)
    }
  }

  private def cachedFilePath(path: String, size: Int) = s"${size}x${size}" + File.separator + path

}

object FileSystemImageCache {

  def apply(root: File, sizes: Set[Int]) = {
    // File manager for cached files.
    val fs = new DefaultFileSystemManager()
    fs.addProvider(Array("file"), new DefaultLocalFileProvider())
    fs.setFilesCache(new SoftRefFilesCache())
    fs.init()
    fs.setBaseFile(root)

    new FileSystemImageCache(root, sizes, fs)
  }
}