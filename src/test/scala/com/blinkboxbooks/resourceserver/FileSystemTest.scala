package com.blinkboxbooks.resourceserver

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FileSystemTest extends FunSuite {

//    test("get extension for filenames with no format conversion") {
//    assert(fileExtension("foo.t") === (Some("t"), None))
//    assert(fileExtension("f.t") === (Some("t"), None))
//    assert(fileExtension("foo.html") === (Some("html"), None))
//    assert(fileExtension("foo.Html") === (Some("html"), None))
//    assert(fileExtension("foo.HTML") === (Some("html"), None))
//    assert(fileExtension("foo.txt") === (Some("txt"), None))
//    assert(fileExtension("foo.bar.txt") === (Some("txt"), None))
//  }
//
//  test("get extension for filenames with no extension") {
//    assert(fileExtension("") === (None, None))
//    assert(fileExtension("f") === (None, None))
//    assert(fileExtension("foo") === (None, None))
//    assert(fileExtension("foo.") === (None, None))
//    assert(fileExtension("foo-bar") === (None, None))
//  }
//
//  test("get extension for filenames with format conversion") {
//    assert(fileExtension("x.jpg.png") === (Some("jpg"), Some("png")))
//    assert(fileExtension("foo.jpg.png") === (Some("jpg"), Some("png")))
//    assert(fileExtension("foo.bar.jpg.png") === (Some("jpg"), Some("png")))
//
//    assert(fileExtension("foo.jpeg.png") === (Some("jpeg"), Some("png")))
//    assert(fileExtension("foo.png.jpeg") === (Some("png"), Some("jpeg")))
//    assert(fileExtension("foo.jpeg.gif") === (Some("jpeg"), Some("gif")))
//    assert(fileExtension("foo.gif.jpeg") === (Some("gif"), Some("jpeg")))
//    assert(fileExtension("foo.JPEG.GIF") === (Some("jpeg"), Some("gif")))
//
//    // What about these cases - what's an extension and what's not? Only known extensions?
//    // Or suitably short extensions?
//    assert(fileExtension("foo.bar.png") === (Some("png"), None))
//  }

//  test("get simple VFS paths") {
//    val simpleFile = "foo/bar/stuff.xml"
//    assert(getVfsPath(simpleFile) === simpleFile)
//    val epubFile = "/foo/bar/stuff.epub"
//    assert(getVfsPath(epubFile) === epubFile)
//    val topLevelEpubFile = "stuff.epub"
//    assert(getVfsPath(topLevelEpubFile) === topLevelEpubFile)
//  }
//
//  test("get VFS container paths") {
//    // Container paths.
//    assert(getVfsPath("foo/bar/stuff.epub/stuff.xml") === "zip:foo/bar/stuff.epub!/stuff.xml")
//    assert(getVfsPath("foo/bar/stuff.epub/dir/stuff.xml") === "zip:foo/bar/stuff.epub!/dir/stuff.xml")
//    assert(getVfsPath("foo/bar/stuff.EPUB/dir/stuff.xml") === "zip:foo/bar/stuff.EPUB!/dir/stuff.xml")
//    assert(getVfsPath("foo/bar/stuff.zip/stuff.xml") === "zip:foo/bar/stuff.zip!/stuff.xml")
//    assert(getVfsPath("foo/bar/stuff.ZIP/stuff.xml") === "zip:foo/bar/stuff.ZIP!/stuff.xml")
//
//    // Containers in containers.
//    assert(getVfsPath("foo/bar/stuff.zip/dir/file.zip") === "zip:foo/bar/stuff.zip!/dir/file.zip")
//    assert(getVfsPath("foo/bar/stuff.zip/dir/file.zip/inner.xml") === "zip:foo/bar/stuff.zip!/dir/file.zip!/inner.xml")
//    assert(getVfsPath("foo/bar/stuff.zip/dir/file.epub/inner.xml") === "zip:foo/bar/stuff.zip!/dir/file.epub!/inner.xml")
//  }
//
//  test("get unsupported container VFS paths") {
//    // Unsupported container extension.
//    val gzipPath = "foo/bar/stuff.gz/stuff.xml"
//    assert(getVfsPath(gzipPath) === gzipPath)
//  }

}
