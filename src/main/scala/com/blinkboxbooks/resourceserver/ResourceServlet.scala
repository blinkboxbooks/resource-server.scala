package com.blinkboxbooks.resourceserver

import java.io.FileInputStream
import java.nio.file._
import javax.servlet.http.HttpServletRequest
import javax.activation.MimetypesFileTypeMap
import org.joda.time.format.ISODateTimeFormat
import org.joda.time._
import org.apache.commons.vfs2._
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.apache.commons.vfs2.provider.zip.ZipFileProvider
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider
import org.apache.commons.codec.digest.DigestUtils
import scala.util.{ Try, Success, Failure }
import org.scalatra.UriDecoder
import org.scalatra.ScalatraServlet
import org.scalatra.util.io.copy
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.vfs2.cache.LRUFilesCache
import org.apache.commons.vfs2.cache.SoftRefFilesCache
import MatrixParameters._

/**
 * A servlet that serves up files, either directly or from inside archive files (e.g. epubs and zips).
 * Image files can optionally be transformed, e.g. resized.
 */
class ResourceServlet(fileSystemManager: FileSystemManager, imageProcessor: ImageProcessor)
  extends ScalatraServlet with Logging with TimeLogging {

  import ResourceServlet._

  private val dateTimeFormat = ISODateTimeFormat.dateTime()
  private val timeFormat = ISODateTimeFormat.time()
  private val mimeTypes = new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))

  /** Direct file access. */
  get("/*") {
    val filename = multiParams("splat").head
    logger.debug(s"Catch-all fallback for direct file access: $filename")
    handleFileRequest(filename)
  }

  /** Access to all files, including inside archives, and with optional image re-sizing. */
  get("""^\/params;([^/]*)/(.*)""".r) {
    time("request") {
      val captures = multiParams("captures")
      val imageParams = getMatrixParams(captures(0)).getOrElse(halt(400, "Invalid parameters supplied"))
      val width = imageParams.get("img:w").map(_.toInt)
      val height = imageParams.get("img:h").map(_.toInt)
      val quality = imageParams.get("img:q").map(_.toInt / 100.0f)
      if (quality.isDefined && (quality.get < 0.0 || quality.get > 1.0))
        halt(400, "Quality parameter must be between 0 and 100")
      val mode = imageParams.get("img:m") map {
        case "scale" | "scale!" => Scale
        case "crop" => Crop
        case "stretch" => Stretch
      }
      val resizeSettings = new ImageSettings(width, height, mode, quality)
      val filename = captures(1)
      logger.debug(s"Request for non-direct file access: $filename, settings=imageSettings")
      handleFileRequest(filename, resizeSettings)
    }
  }

  before() {
    response.characterEncoding = None
    val expiryTime = Duration.standardDays(365)
    response.headers += ("expires_in" -> expiryTime.getStandardSeconds.toString)
    response.headers += ("Cache-Control" -> s"public, max-age=${expiryTime.getStandardSeconds}")
    val now = new DateTime()
    response.headers += ("now" -> timeFormat.print(now))
    response.headers += ("Date" -> dateTimeFormat.print(now))
    response.headers += ("Expires" -> (now plus expiryTime).toString)
    if (response.status.code == 200) response.headers += ("ETag" -> uriHash(request.getRequestURI))
    response.headers += ("X-Application-Version" -> "0.0.1")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  /** Serve up file, by looking it up in a virtual filesystem and applying any transforms. */
  private def handleFileRequest(filename: String, imageSettings: ImageSettings = unchanged) {
    if (filename.endsWith(".key")) {
      logger.info(s"$filename rejected as I never send keyfiles")
      halt(404, "The requested resource does not exist here")
    }

    val vfsPath = getVfsPath(filename)
    val file = Try(fileSystemManager.resolveFile(vfsPath))
    if (file.isFailure || !file.get.exists || !file.get.getType.equals(FileType.FILE)) {
      logger.info(s"filename rejected as the file doesn't exist")
      halt(404, "The requested resource does not exist here")
    }

    contentType = mimeTypes.getContentType(filename.toString)

    val input = file.get.getContent().getInputStream()
    val targetFileType = fileExtension(filename.toString).getOrElse(halt(400, s"Requested file '$filename' has no extension"))
    if (imageSettings.hasSettings) {
      time("transform") {
        imageProcessor.transform(targetFileType, input, response.getOutputStream, imageSettings)
      }
    } else {
      copy(input, response.getOutputStream)
    }
  }

  /**
   * The default Scalatra implementation treats everything after a semi-colon as request parameters,
   * we have to override this to cope with matrix parameters.
   */
  override def requestPath(implicit request: HttpServletRequest) = request.getRequestURI

}

object ResourceServlet {

  /** Factory method for creating servlet. */
  def apply(rootDirectory: Path): ScalatraServlet = {
    // Create a file system manager that resolves paths in ePub and Zip files, 
    // as well as regular files.
    val fsManager = new DefaultFileSystemManager()
    fsManager.addProvider(Array("zip"), new ZipFileProvider())
    fsManager.addProvider(Array("file"), new DefaultLocalFileProvider())
    fsManager.setFilesCache(new SoftRefFilesCache())
    fsManager.init()
    fsManager.setBaseFile(rootDirectory.toFile)

    new ResourceServlet(fsManager, new SynchronousScalrImageProcessor())
  }

  /** @return Hex string of MD5 hash for the given input. */
  def uriHash(str: String) = DigestUtils.md5Hex(str) // TODO: Need to make sure this is 100% the same as Ruby's hashes!

  /**
   * @return the given path with container files (epubs, zips) referred to using
   * VFS syntax, by appending a "!". E.g. "dir/foo.epub/some/file.html" => "zip:dir/foo.epub!/some/file.html".
   */
  def getVfsPath(filename: String) = {
    // Add exclamation mark after .epub or .zip, except at the end of the path.
    val updated = """(?i)(\.epub|\.zip)/""".r.replaceAllIn(filename, """$1!/""")
    // Add 'zip:' prefix if the path contains at least one archive file.
    if (updated == filename) filename else "zip:" + updated
  }

  /** @return lower case file extension of given file name. */
  def fileExtension(filename: String) = filename.lastIndexOf(".") match {
    case -1 => None
    case pos => Some(filename.substring(pos + 1, filename.size).toLowerCase)
  }

  val unchanged = new ImageSettings()

}
