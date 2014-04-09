package com.blinkboxbooks.resourceserver

import java.io.File
import java.io.FileInputStream
import java.nio.file._
import javax.servlet.http.HttpServletRequest
import javax.activation.MimetypesFileTypeMap
import org.joda.time.format.ISODateTimeFormat
import org.apache.commons.vfs2.FileSystemManager
import org.apache.commons.vfs2.FileType
import org.apache.commons.codec.digest.DigestUtils
import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration.Duration
import org.scalatra.UriDecoder
import org.scalatra.ScalatraServlet
import org.scalatra.util.io.copy
import com.typesafe.scalalogging.slf4j.Logging
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import resource._
import MatrixParameters._
import Utils._
import FileSystem._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * A servlet that serves up files, either directly or from inside archive files (e.g. epubs and zips).
 * Image files can optionally be transformed, e.g. resized.
 */
class ResourceServlet(fileSystemManager: FileSystemManager,
  imageProcessor: ImageProcessor, cache: ImageCache, cacheingContext: ExecutionContext)
  extends ScalatraServlet with Logging with TimeLogging {

  import ResourceServlet._
  import Gravity._

  private val dateTimeFormat = DateTimeFormat.forPattern("E, d MMM yyyy HH:mm:ss Z");
  private val timeFormat = ISODateTimeFormat.time()
  private val mimeTypes = new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))
  private val characterEncodingForFiletype = Map("css" -> "utf-8", "js" -> "utf-8")
  private val unchanged = new ImageSettings()

  val MAX_DIMENSION = 2500

  before() {
    response.characterEncoding = None
    val expiryTime = org.joda.time.Duration.standardDays(365)
    response.headers += ("expires_in" -> expiryTime.getStandardSeconds.toString)
    response.headers += ("Cache-Control" -> s"public, max-age=${expiryTime.getStandardSeconds}")
    val now = new DateTime()
    response.headers += ("now" -> timeFormat.print(now))
    response.headers += ("Date" -> dateTimeFormat.print(now))
    response.headers += ("Expires" -> dateTimeFormat.print(now plus expiryTime))
    response.headers += ("X-Application-Version" -> "0.0.1")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

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
      val imageParams = getMatrixParams(captures(0)).getOrElse(halt(400, "Invalid parameter syntax"))
      val width = intParam(imageParams, "img:w")
      if (width.isDefined && (width.get <= 0 || width.get > MAX_DIMENSION))
        halt(400, s"Width must be between 1 and $MAX_DIMENSION, got ${width.get}")
      val height = intParam(imageParams, "img:h")
      if (height.isDefined && (height.get <= 0 || height.get > MAX_DIMENSION))
        halt(400, s"Height must be between 1 and $MAX_DIMENSION, got ${height.get}")
      val quality = intParam(imageParams, "img:q").map(_.toInt / 100.0f)
      if (quality.isDefined && (quality.get <= 0.0 || quality.get > 1.0))
        halt(400, "Quality parameter must be between 0 and 100")
      val mode = imageParams.get("img:m") map {
        case "scale" | "scale!" => Scale
        case "crop" => Crop
        case "stretch" => Stretch
        case m @ _ => invalidParameter("img:m", m)
      }
      val gravity = gravityParam(imageParams, "img:g")
      val imageSettings = new ImageSettings(width, height, mode, quality, gravity)
      val filename = captures(1)
      logger.debug(s"Request for non-direct file access: $filename, settings=$imageSettings")
      handleFileRequest(filename, imageSettings)
    }
  }

  error {
    case e =>
      logger.error("Unexpected error for request: " + request.getRequestURI, e)
      halt(500, "Unexpected error: " + e.getMessage)
  }

  /** Serve up file, by looking it up in a virtual file system and applying any transforms. */
  private def handleFileRequest(filename: String, imageSettings: ImageSettings = unchanged) {
    if (filename.endsWith(".key")) {
      logger.info(s"$filename rejected as I never send keyfiles")
      halt(404, "The requested resource does not exist here")
    }

    val byteRange = Utils.range(Option(request.getHeader("Range")))

    val (originalExtension, targetExtension) = fileExtension(filename)
    val targetFileType = targetExtension.getOrElse(originalExtension.getOrElse(halt(400, s"Requested file '$filename' has no extension")))

    val baseFilename = if (targetExtension.isDefined) filename.dropRight(targetExtension.get.size + 1) else filename

    // Look for cached file if a smaller image has been requested.
    val cachedFile = imageSettings.maximumDimension.flatMap(size => cache.getImage(baseFilename, size))
    for (file <- managed(cachedFile.getOrElse(getVfsFile(baseFilename)))) {

      if (!cachedFile.isDefined && imageSettings.hasSettings && cache.wouldCacheImage(imageSettings.maximumDimension)) {
        Future { cache.addImage(baseFilename, file) }(cacheingContext)
      }

      contentType = mimeTypes.getContentType("file." + targetFileType)
      characterEncodingForFiletype.get(targetFileType.toLowerCase).foreach(response.setCharacterEncoding(_))
      response.headers += ("Content-Location" -> request.getRequestURI) // Canonicalise this?
      response.headers += ("ETag" -> stringHash(request.getRequestURI))

      // Get input and output and skip and truncate results if requested.
      for (
        inputStream <- managed(file.getContent().getInputStream());
        boundedInput <- managed(boundedInputStream(inputStream, byteRange))
      ) {
        if (imageSettings.hasSettings || targetExtension.isDefined) {
          time("transform", Debug) { imageProcessor.transform(targetFileType, boundedInput, response.getOutputStream, imageSettings) }
        } else {
          time("direct write", Debug) { copy(boundedInput, response.getOutputStream) }
        }
      }
    }
  }

  /**
   * The default Scalatra implementation treats everything after a semi-colon as request parameters,
   * we have to override this to cope with matrix parameters.
   */
  override def requestPath(implicit request: HttpServletRequest) = request.getRequestURI

  private def intParam(parameters: Map[String, String], name: String): Option[Int] =
    parameters.get(name).map(str => Try(str.toInt) getOrElse invalidParameter(name, str))

  private def gravityParam(parameters: Map[String, String], name: String): Option[Gravity] =
    parameters.get(name).map(str => Try(Gravity.withName(str)) getOrElse invalidParameter(name, str))

  private def invalidParameter(name: String, value: String) = halt(400, s"'$value' is not a valid value for '$name'")

  /**
   * Look up file in file system. Fail request if it's not found.
   */
  private def getVfsFile(baseFilename: String) = {
    val vfsPath = getVfsPath(baseFilename)
    val file = Try(fileSystemManager.resolveFile(vfsPath))
    if (file.isFailure || !file.get.exists || !file.get.getType.equals(FileType.FILE)) {
      logger.info(s"Request for $baseFilename rejected as the file doesn't exist")
      halt(404, "The requested resource does not exist here")
    }
    file.get
  }

}

object ResourceServlet {

  /** Factory method for creating a servlet backed by a file system. */
  def apply(fsManager: FileSystemManager, cache: ImageCache, cacheingContext: ExecutionContext,
    numResizingThreads: Int, info: Duration, warning: Duration, err: Duration): ScalatraServlet = {

    trait Thresholds extends TimeLoggingThresholds {
      override def infoThreshold = info
      override def warnThreshold = warning
      override def errorThreshold = err
    }

    new ResourceServlet(fsManager, new ThreadPoolImageProcessor(numResizingThreads), cache, cacheingContext) with Thresholds
  }

}
