package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import java.nio.file.Files
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Path
import java.io.IOException
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import org.apache.commons.io.IOUtils
import java.io.InputStream
import scala.util.Try
import java.util.Arrays
import java.nio.file.Paths

@RunWith(classOf[JUnitRunner])
class EpubEnabledFileResolverTest extends FunSuite with BeforeAndAfterAll {

  import EpubEnabledFileResolver._

  val KeyFile = "secret.key"
  val TopLevelFile = "toplevel.html"
  val topLevel = Files.createTempDirectory(getClass.getName)
  val rootDir = topLevel.resolve("root")
  val contentDir = rootDir.resolve("content")
  val subDir = contentDir.resolve("sub")
  Files.createDirectories(subDir)
  val resolver = new EpubEnabledFileResolver(rootDir)

  override def beforeAll() {
    super.beforeAll()

    FileUtils.write(topLevel.resolve(TopLevelFile).toFile, "Should not be accessible")
    FileUtils.write(rootDir.resolve(KeyFile).toFile, "Don't serve this up")
    FileUtils.write(rootDir.resolve("ch01.html").toFile, "<p>It was a dark and stormy night...</p>")
    FileUtils.write(subDir.resolve("ch02.html").toFile, "<p>and the wind was blowing a gale.</p>")
    FileUtils.write(subDir.resolve("invalid.epub").toFile, "This is not an ePub file")
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.gif"), rootDir.resolve("test.gif").toFile)
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.jpeg"), rootDir.resolve("test.jpeg").toFile)
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.png"), rootDir.resolve("test.png").toFile)
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.epub"), rootDir.resolve("test.epub").toFile)
    FileUtils.copyInputStreamToFile(getClass.getResourceAsStream("/test.epub"), subDir.resolve("test2.epub").toFile)
  }

  override def afterAll() {
    FileUtils.deleteDirectory(topLevel.toFile)
  }

  test("direct file lookup") {
    val expectedContent = "<p>It was a dark and stormy night...</p>"
    val input = resolver.resolve("ch01.html")
    assert(content(input) === expectedContent)
  }

  test("direct file lookup in subdirectory") {
    val expectedContent = "<p>and the wind was blowing a gale.</p>"
    val input = resolver.resolve("content/sub/ch02.html")
    assert(content(input) === expectedContent)
  }

  test("direct lookup of missing file") {
    val filename = "unknown.file"
    val input = resolver.resolve(filename)
    val ex = intercept[NoSuchFileException] { input.get }
    assert(ex.getMessage.contains(filename))
  }

  test("file lookup above root directory") {
    val filename = "../" + TopLevelFile
    val input = resolver.resolve(filename)
    val ex = intercept[AccessDeniedException] { input.get }
    assert(ex.getMessage.contains(filename))
  }

  test("get file inside epub") {
    val expectedContent = "<p>Welcome dear reader!</p>\n"
    val input = resolver.resolve("test.epub/content/intro.html")
    assert(content(input) === expectedContent)
  }

  test("get file inside epub in subdirectory") {
    val expectedContent = "<p>Welcome dear reader!</p>\n"
    val input = resolver.resolve("content/sub/test2.epub/content/intro.html")
    assert(content(input) === expectedContent)
  }

  test("try to get missing file inside epub") {
    val path = "sub/test2.epub/content/unknown"
    val ex = intercept[NoSuchFileException] { resolver.resolve(path).get }
  }

  test("try to get file inside missing epub") {
    val path = "sub/unknown.epub/content/intro.html"
    intercept[NoSuchFileException] { resolver.resolve(path).get }
  }

  test("try to get file from invalid epub") {
    val path = "invalid.epub/foo"
    intercept[FileNotFoundException] { resolver.resolve(path).get }
  }

  test("badly configured resolver") {
    intercept[IOException] { new EpubEnabledFileResolver(Paths.get("does", "not", "exist")) }
  }

  test("parse valid paths with no epub parent") {
    Seq("", "/", "foo", "foo.bar", "dir/foo", "dir/foo.bar", "foo.epub", "dir1/dir2/dir3/foo.epub").foreach(path => {
      assert(parseEpubPath(path) === (None, path))
    })
  }

  test("parse valid paths that do contain an epub parent") {
    Map("foo.epub/file" -> (Some("foo.epub"), "file"),
      "foo.epub/file.ext" -> (Some("foo.epub"), "file.ext"),
      "dir/bar.epub/file" -> (Some("dir/bar.epub"), "file"),
      "foo.epub/content/foo.bar" -> (Some("foo.epub"), "content/foo.bar")).foreach({
        case (input, (Some(epubPath), filePath)) =>
          assert(parseEpubPath(input) === (Some(epubPath), filePath))
      })
  }

  def content(input: Try[InputStream]) = IOUtils.toString(input.get, "UTF-8")

}
