package com.blinkboxbooks.resourceserver

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}
import java.nio.file.{FileSystem, Files, Path}

import com.google.common.jimfs.{Configuration, Jimfs}
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import resource._

import scala.collection.JavaConversions._
import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
class FileSystemImageCacheTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll with ImageChecks with MockitoSugar {

  val MinSize = 50
  val MidSize = 100
  val MaxSize = 200
  val sizes = Set(MidSize, MinSize, MaxSize)
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

    cache = new FileSystemImageCache(cacheDir, sizes, resolver, writingEnabled = true)
  }

  after {
    reset(resolver)
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

    for (width <- Seq(MinSize / 2, MinSize - 1, MinSize)) {
      checkIsCached(width, MinSize)
    }

    for (width <- Seq(MinSize + 1, MinSize + 5, MinSize + ((MidSize - MinSize) / 2), MidSize - 1, MidSize)) {
      checkIsCached(width, MidSize)
    }

    for (width <- Seq(MidSize + 1, MidSize + ((MaxSize - MidSize) / 2), MaxSize - 1, MaxSize)) {
      checkIsCached(width, MaxSize)
    }

    for (width <- Seq(MaxSize + 1, MaxSize + 10)) {
      assert(cache.getImage(filePath, width) === None)
    }
  }

  test("add image multiple times") {
    addImage(filePath)
    doReturn(Success(new ByteArrayInputStream(largeFile.toByteArray))).when(resolver).resolve(filePath)
    addImage(filePath)

    // Should just work as normal.
    for (width <- Seq(MinSize / 2, MinSize - 1, MinSize)) {
      checkIsCached(width, MinSize)
    }
  }

  test("run out of disk space while writing cached file") {
    // Create an in-memory file system with very little space (the size of
    // the first file plus a bit).
    fs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setMaxSize(1000).build())
    cacheDir = fs.getPath("/cache")
    Files.createDirectories(cacheDir)
    cache = new FileSystemImageCache(cacheDir, sizes, resolver, writingEnabled = true)

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

  test("test with cache population disabled") {
    cache = new FileSystemImageCache(cacheDir, sizes, resolver, writingEnabled = false)

    // Try to add image.
    addImage(filePath)

    // Should ignore the request, hence shouldn't hit the file system at all.
    verifyNoMoreInteractions(resolver)
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
