package com.blinkboxbooks.resourceserver

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.spi.FileSystemProvider
import java.nio.file.FileSystem
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
import org.imgscalr.Scalr
import org.apache.commons.io.IOUtils
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import TestUtils._
import com.google.jimfs.Jimfs
import com.google.jimfs.Configuration
import collection.JavaConversions._

@RunWith(classOf[JUnitRunner])
class FileSystemImageCacheTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll with ImageChecks with MockitoSugar {

  val sizes = Set(500, 100, 1000)
  val filePath = "some/path/to/file/test.png"

  var fs: FileSystem = _
  var cacheDir: Path = _
  var workDir: Path = _
  var cache: ImageCache = _

  override def beforeAll() {
    // Create test files that we can add to the cache in tests.s
    workDir = Files.createTempDirectory(getClass.getName)
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/large.png"), workDir.resolve("test.png").toFile)
    FileUtils.writeStringToFile(workDir.resolve("invalid.png").toFile, "This is not a PNG file")
  }

  before {
    // Create an in-memory file system for the cache.
    fs = Jimfs.newFileSystem(Configuration.unix())
    cacheDir = fs.getPath("/cache")
    Files.createDirectories(cacheDir)

    cache = new FileSystemImageCache(cacheDir, sizes)
  }

  after {
    fs.close()
  }

  override def afterAll() {
    FileUtils.deleteDirectory(workDir.toFile)
  }

  def getImage(): Path = workDir.resolve("test.png")

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

  test("run out of disk space while writing cached file") {
    // Create an in-memory file system with very little space (the size of
    // the first file plus a bit).
    fs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setMaxSize(1000).build())
    cacheDir = fs.getPath("/cache")
    Files.createDirectories(cacheDir)
    cache = new FileSystemImageCache(cacheDir, sizes)

    intercept[IOException] { cache.addImage(filePath, getImage()) }

    // Check that the partially written file was deleted, no new files should be left behind.
    assert(getAllPaths(cacheDir).filter(!Files.isDirectory(_)).size === 0)
  }

  test("try to add invalid image file") {
    intercept[IOException] { cache.addImage(filePath, workDir.resolve("invalid.png")) }
  }

  test("try to add non-existant image file") {
    intercept[IOException] { cache.addImage(filePath, workDir.resolve("doesnt-exist.png")) }
  }

  def checkIsCached(requestedWidth: Int, cachedWidth: Int) {
    val path = cache.getImage(filePath, requestedWidth)
    assert(path.isDefined && Files.exists(path.get), s"Should find a file with size greater than $requestedWidth")
    checkImage(Files.newInputStream(path.get), "png", cachedWidth)
  }

  def getAllPaths(p: Path): Stream[Path] =
    p #:: (if (Files.isDirectory(p)) listPaths(p).toStream.flatMap(getAllPaths)
    else Stream.empty)

  def listPaths(p: Path): Stream[Path] = Stream() ++ Files.newDirectoryStream(p).iterator()
}
