package com.blinkboxbooks.resourceserver

import java.io.InputStream
import java.net.URLDecoder
import java.nio.file._
import java.util.Locale
import java.util.concurrent.RejectedExecutionException
import javax.activation.MimetypesFileTypeMap
import javax.servlet.http.HttpServletRequest

import com.blinkboxbooks.resourceserver.MatrixParameters._
import com.blinkboxbooks.resourceserver.Utils._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatra.ScalatraServlet
import org.scalatra.util.io.copy

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A servlet that serves up files, either directly or from inside archive files (e.g. epubs and zips).
 * Image files can optionally be transformed, e.g. resized.
 */
class ResourceServlet(resolver: FileResolver,
                      imageProcessor: ImageProcessor, cache: ImageCache, cacheingContext: ExecutionContext)
  extends ScalatraServlet with StrictLogging with HttpMonitoring with TimeLogging {

  import Gravity._
  import ResourceServlet._
  import resource._

  import scala.io.Source

  private val dateTimeFormat = DateTimeFormat.forPattern("E, d MMM yyyy HH:mm:ss 'GMT'")
    .withLocale(Locale.US)
    .withZone(DateTimeZone.UTC)
  private val timeFormat = ISODateTimeFormat.time()
  private val mimeTypes = new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))
  private val characterEncodingForFiletype = Map("css" -> "utf-8", "js" -> "utf-8")
  private val unchanged = new ImageSettings()
  private val ApplicationVersion = Try(Source.fromFile("VERSION").mkString).getOrElse("0.0.0")

  before() {
    response.characterEncoding = None
    val expiryTime = org.joda.time.Duration.standardDays(365)
    response.headers += ("Cache-Control" -> s"public, max-age=${expiryTime.getStandardSeconds}")
    val now = new DateTime()
    response.headers += ("Date" -> dateTimeFormat.print(now))
    response.headers += ("Expires" -> dateTimeFormat.print(now plus expiryTime))
    response.headers += ("X-Application-Version" -> ApplicationVersion)
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  /** Direct file access. */
  get("/*") {
    monitor(request, response) {
      val filename = multiParams("splat").head
      logger.debug(s"Catch-all fallback for direct file access: $filename")
      val byteRange = Range(Option(request.getHeader("Range")))
      handleFileRequest(URLDecoder.decode(filename, "UTF-8"), byteRange)
    }
  }

  /** Access to all files, including inside archives, and with optional image re-sizing. */
  get("""^\/params(?:;|%3[Bb])([^/]*)/(.*)""".r) {
    monitor(request, response) {
      val captures = multiParams("captures")
      val params = URLDecoder.decode(captures(0), "UTF-8")
      val imageParams = getMatrixParams(params).getOrElse(halt(400, "Invalid parameter syntax"))

      val filename = relativePath(URLDecoder.decode(captures(1), "UTF-8"))
      val requestIsForImage = fileExtension(filename) match {
        case (Some(ext), _) if ACCEPTED_IMAGE_FORMATS.contains(ext) => true
        case _ => false
      }

      if (requestIsForImage) {
        // Check that version is well known, otherwise return an error.
        imageParams.get("v") match {
          case Some("0")                         => // OK.
          case Some(v) if Try(v.toInt).isFailure => halt(400, s"Server version should be specified as an integer value")
          case Some(v)                           => halt(400, s"Server version $v is not yet specified")
          case None                              => halt(400, "No version specified")
        }

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
          case "scale"   => ScaleWithoutUpscale
          case "scale!"  => ScaleWithUpscale
          case "crop"    => Crop
          case "stretch" => Stretch
          case m @ _     => invalidParameter("img:m", m)
        }

        val gravity = gravityParam(imageParams, "img:g")

        val imageSettings = new ImageSettings(width, height, mode, quality, gravity)
        logger.debug(s"Request for non-direct file access: $filename, settings=$imageSettings")
        handleFileRequest(filename, byteRange = None, imageSettings)
      } else {
        handleFileRequest(filename, byteRange = None)
      }

    }
  }

  error {
    case e =>
      logger.error("Unexpected error for request: " + request.getRequestURI, e)
      response.reset()
      halt(500, "Unexpected error: " + e.getMessage)
  }

  /** Serve up file, by looking it up in a virtual file system and applying any transforms. */
  private def handleFileRequest(filename: String, byteRange: Option[Range], imageSettings: ImageSettings = unchanged) {
    if (Paths.get(filename).normalize.toString.endsWith(".key")) {
      logger.info(s"$filename rejected as I never send keyfiles")
      halt(404, "The requested resource does not exist here")
    }

    val (originalExtension, targetExtension) = fileExtension(filename)
    val targetFileType = targetExtension
      .getOrElse(originalExtension
        .getOrElse(halt(400, s"Requested file '$filename' has no extension")))

    val baseFilename = if (targetExtension.isDefined) filename.dropRight(targetExtension.get.size + 1) else filename

    // Set the status code that Scalatra will use for the response according to whether it's full or partial.
    status = byteRange.fold(200)(_ => 206)

    // Look for cached file if requesting a transformed image.
    val cachedImage = imageSettings.maximumDimension.flatMap(size => cache.getImage(baseFilename, size))
    if (cachedImage.isDefined) {
      response.headers += (CACHE_INDICATION_HEADER -> "true")
    }

    for (inputStream <- managed(cachedImage.getOrElse(checkedInput(resolver.resolve(baseFilename))))) {
      contentType = mimeTypes.getContentType("file." + targetFileType)
      characterEncodingForFiletype.get(targetFileType.toLowerCase).foreach(response.setCharacterEncoding(_))
      val etag = "\"" + stringHash(request.getRequestURI) + "\""
      response.headers += ("ETag" -> etag)

      // Truncate results if requested.
      val boundedInput = Range.boundedInputStream(inputStream, byteRange)

      // Write resulting data.
      if (imageSettings.hasSettings || targetExtension.isDefined) {
        val callback: ImageSettings => Unit = (effectiveSettings) => {
          response.headers += ("Content-Location" -> canonicalUri(baseFilename, effectiveSettings))
        }
        time("transform", Debug) { imageProcessor.transform(targetFileType, boundedInput, response.getOutputStream, imageSettings, Some(callback)) }
      } else {
        response.headers += ("Content-Location" -> request.getRequestURI)
        time("direct write", Debug) { copy(boundedInput, response.getOutputStream) }
      }

      // Add background task to cache image.
      if (!cachedImage.isDefined && imageSettings.hasSettings && cache.wouldCacheImage(imageSettings.maximumDimension)) {
        enqueueImage(baseFilename)
      }
    }
  }

  /**
   * The default Scalatra implementation treats everything after a semi-colon as request parameters,
   * we have to override this to cope with matrix parameters.
   */
  override def requestPath(implicit request: HttpServletRequest) = request.getRequestURI

  // this converts to a float first and then to an int to support floating point numbers which Tesco took a
  // dependency on for the Hudl2 promotion cards. we don't want to do this, but have no choice.
  private def intParam(parameters: Map[String, String], name: String): Option[Int] =
    parameters.get(name).map(str => Try(str.toFloat.toInt) getOrElse invalidParameter(name, str))

  private def gravityParam(parameters: Map[String, String], name: String): Option[Gravity] =
    parameters.get(name).map(str => Try(Gravity.withName(str)) getOrElse invalidParameter(name, str))

  private def invalidParameter(name: String, value: String) = halt(400, s"'${safeValue(value)}' is not a valid value for '$name'")

  private def safeValue(value: String): String = xml.Utility.escape(value)

  private def relativePath(path: String): String = path.dropWhile(_ == '/')

  private def checkedInput(input: Try[InputStream]) = input match {
    case Success(path) => path
    case Failure(e: AccessDeniedException) =>
      logger.info("Request for invalid path rejected: " + e.getMessage)
      halt(400, "The requested resource path is not accessible")
    case Failure(e) =>
      logger.info("Request rejected as the file doesn't exist: " + e.getMessage)
      halt(404, "The requested resource does not exist here")
  }

  private def enqueueImage(filename: String) =
    Try(Future { cache.addImage(filename) }(cacheingContext)) match {
      case Failure(e: RejectedExecutionException) => logger.warn("Failed to enqueue image for caching: " + e.getMessage)
      case _                                      =>
    }

}

object ResourceServlet {

  val MAX_DIMENSION = 2500
  val CACHE_INDICATION_HEADER = "X-bbb-from-intermediate-resource"

  /** Factory method for creating a servlet backed by a file system. */
  def apply(resolver: FileResolver, cache: ImageCache, cacheingContext: ExecutionContext,
            numResizingThreads: Int, info: Duration, warning: Duration, err: Duration): ScalatraServlet = {

    trait Thresholds extends TimeLoggingThresholds {
      override def infoThreshold = info
      override def warnThreshold = warning
      override def errorThreshold = err
    }
    new ResourceServlet(resolver, new ThreadPoolImageProcessor(numResizingThreads), cache, cacheingContext) with Thresholds
  }

}
