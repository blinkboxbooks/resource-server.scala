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

class FileSystemImageCache(root: File, sizes: List[Int]) extends ImageCache with Logging {

  val targetSizes = sizes.sorted

  val fs = new DefaultFileSystemManager()
  fs.addProvider(Array("file"), new DefaultLocalFileProvider())
  fs.setFilesCache(new SoftRefFilesCache())
  fs.init()
  fs.setBaseFile(root)

  def addImage(path: String, content: FileObject) {
    logger.debug(s"Caching image at $path")
    val input = content.getContent().getInputStream()
    try {
      val image = ImageIO.read(input)
      if (image == null) throw new IOException(s"Unable to decode image at $path")
      for (size <- targetSizes) {
        val resized = Scalr.resize(image, Method.BALANCED, Mode.AUTOMATIC, size, Scalr.OP_ANTIALIAS)
        val outputPath = cachedFilePath(path, size)
        val outputFile = fs.resolveFile(outputPath)
        if (outputFile.exists) {
          outputFile.delete()
        }
        outputFile.createFile()
        val output = outputFile.getContent.getOutputStream()
        try {
          writeFile(resized, output)
          logger.debug("Wrote output file: " + outputFile + " with dimensions (" + resized.getWidth() + "[w], "
            + resized.getHeight() + "[h])")
        } finally {
          output.close()
        }
      }
    } finally {
      input.close()
    }
  }

  def getImage(path: String, minSize: Int): Option[FileObject] = {
    val cachedSize = targetSizes.find(_ >= minSize)
    if (cachedSize == None) return None

    val cachedPath = cachedFilePath(path, cachedSize.get)
    logger.debug(s"Using cached image at $cachedPath")
    val file = fs.resolveFile(cachedPath)
    if (file.exists) Some(file) else None
  }

  def wouldCacheImage(size: Option[Int]) = size.isDefined && size.get <= targetSizes.max

  private def writeFile(image: BufferedImage, output: OutputStream) {
    val writer = ImageIO.getImageWritersByFormatName("png").next()
    val imageOutputStream = new MemoryCacheImageOutputStream(output)
    try {
      writer.setOutput(imageOutputStream)
      writer.write(image)
    } finally {
      output.close()
    }
  }

  private def cachedFilePath(path: String, size: Int) = s"${size}x${size}" + File.separator + path

}