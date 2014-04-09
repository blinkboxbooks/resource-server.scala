package com.blinkboxbooks.resourceserver

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConversions._
import scala.util.Success
import org.apache.commons.io.FileUtils
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import com.google.jimfs.Configuration
import com.google.jimfs.Jimfs
import TestUtils._
import resource._
import scala.util.Failure
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream

@RunWith(classOf[JUnitRunner])
class FileSystemImageCacheTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll with ImageChecks with MockitoSugar {

  val sizes = Set(500, 100, 1000)
  val filePath = "some/path/to/file/test.png"

  val largeFile = new ByteArrayOutputStream()
  IOUtils.copy(getClass.getResourceAsStream("/large.png"), largeFile)

  var fs: FileSystem = _
  var cacheDir: Path = _
  var cache: ImageCache = _
  var resolver: FileResolver = _

  before {
    // Create an in-memory file system for the cache.
    fs = Jimfs.newFileSystem(Configuration.unix())
    cacheDir = fs.getPath("/cache")
    Files.createDirectories(cacheDir)

    resolver = mock[FileResolver]
    doReturn(Success(new ByteArrayInputStream(largeFile.toByteArray))).when(resolver).resolve(filePath)

    cache = new FileSystemImageCache(cacheDir, sizes, resolver)
  }

  after {
    fs.close()
  }

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
    addImage(filePath)

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
    addImage(filePath)
    doReturn(Success(new ByteArrayInputStream(largeFile.toByteArray))).when(resolver).resolve(filePath)
    addImage(filePath)

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
    cache = new FileSystemImageCache(cacheDir, sizes, resolver)

    intercept[IOException] { addImage(filePath) }

    // Check that the partially written file was deleted, no new files should be left behind.
    assert(getAllPaths(cacheDir).filter(!Files.isDirectory(_)).size === 0)
  }

  test("try to add invalid image file") {
    doReturn(Success(new ByteArrayInputStream("Not a PNG file".getBytes())))
      .when(resolver).resolve(anyString)
    intercept[IOException] { cache.addImage(filePath) }
  }

  test("try to add non-existant image file") {
    val ex = new IOException("test execption")
    doReturn(Failure(ex)).when(resolver).resolve("unknown.png")
    val returnedEx = intercept[IOException] { cache.addImage("unknown.png") }
    assert(ex eq returnedEx)
  }

  private def checkIsCached(requestedWidth: Int, cachedWidth: Int) =
    cache.getImage(filePath, requestedWidth) match {
      case Some(input) =>
        for (i <- managed(input)) { checkImage(input, "png", cachedWidth) }
      case None => fail(s"Should find a file with size greater than $requestedWidth")
    }

  /** Recursively get all paths that are at or below the given one. */
  private def getAllPaths(p: Path): Stream[Path] =
    p #:: (if (Files.isDirectory(p)) listPaths(p).toStream.flatMap(getAllPaths)
    else Stream.empty)

  private def listPaths(p: Path): Stream[Path] = Stream() ++ Files.newDirectoryStream(p).iterator()

  private def addImage(path: String) =
    for (image <- managed(new ByteArrayInputStream(largeFile.toByteArray))) {
      cache.addImage(path)
    }

}
