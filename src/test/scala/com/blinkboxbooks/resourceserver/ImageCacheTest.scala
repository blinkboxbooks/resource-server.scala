package com.blinkboxbooks.resourceserver

import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import javax.imageio.stream.FileImageOutputStream
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.junit.JUnitRunner
import org.apache.commons.io.FileUtils
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider
import org.apache.commons.vfs2.FileObject
import org.imgscalr.Scalr
import org.apache.commons.io.IOUtils

@RunWith(classOf[JUnitRunner])
class FileSystemImageCacheTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll with ImageChecks {

  val sizes = Set(500, 100, 1000)
  val filePath = "some/path/to/file/test.png"
  val fsManager = new DefaultFileSystemManager()

  var cacheDir: File = _
  var workDir: File = _
  var cache: ImageCache = _

  override def beforeAll() {
    workDir = Files.createTempDirectory(getClass.getName).toFile

    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/large.png"), new File(workDir, "test.png"))
    FileUtils.writeStringToFile(new File(workDir, "invalid.png"), "This is not a PNG file")

    fsManager.addProvider(Array("file"), new DefaultLocalFileProvider())
    fsManager.init()
    fsManager.setBaseFile(workDir)
  }

  before {
    cacheDir = Files.createTempDirectory(getClass.getName).toFile
    cache = new FileSystemImageCache(cacheDir, sizes)
  }

  after {
    FileUtils.deleteDirectory(cacheDir)
  }

  override def afterAll() {
    FileUtils.deleteDirectory(workDir)
  }

  def getImage() = fsManager.resolveFile(workDir, "test.png")

  test("would cache image") {
    assert(!cache.wouldCacheImage(None))
    assert(cache.wouldCacheImage(Some(sizes.head)))
    assert(cache.wouldCacheImage(Some((sizes.head + sizes.last) / 2)))
    assert(cache.wouldCacheImage(Some(sizes.last)))
    assert(!cache.wouldCacheImage(Some(sizes.last + 1)))
  }

  test("get unknown image") {
    assert(cache.getImage("unknown.png", sizes.head) === None)
  }

  test("add then get image at various sizes") {
    cache.addImage(filePath, getImage())

    for (width <- Seq(50, 99, 100)) {
      checkIsCached(width, 100)
    }

    for (width <- Seq(101, 150, 250, 499, 500)) {
      checkIsCached(width, 500)
    }

    for (width <- Seq(501, 750, 999, 1000)) {
      checkIsCached(width, 1000)
    }

    for (width <- Seq(1001, 2000)) {
      assert(cache.getImage(filePath, width) === None)
    }
  }

  test("add image multiple times") {
    cache.addImage(filePath, getImage())
    cache.addImage(filePath, getImage())

    // Should just work as normal.
    for (width <- Seq(50, 99, 100)) {
      checkIsCached(width, 100)
    }
  }

  test("try to add invalid image file") {
    intercept[IOException] { cache.addImage(filePath, fsManager.resolveFile(workDir, "invalid.png")) }
  }

  test("try to add non-existant image file") {
    intercept[IOException] { cache.addImage(filePath, fsManager.resolveFile(workDir, "doesnt-exist.png")) }
  }

  def checkIsCached(requestedWidth: Int, cachedWidth: Int) {
    val file = cache.getImage(filePath, requestedWidth)
    assert(file.isDefined, s"Should find a file with size greater than $requestedWidth")
    checkImage(file.get.getContent().getInputStream(), "png", cachedWidth)
  }

}
