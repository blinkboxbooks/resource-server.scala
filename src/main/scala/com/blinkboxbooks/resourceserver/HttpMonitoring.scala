package com.blinkboxbooks.resourceserver

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.blinkbox.books.jar.JarManifest
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.MDC

object HttpMonitoring {
  private val requestHeaderMdcKeys = Map(
    "Accept-Encoding" -> "httpAcceptEncoding",
    "User-Agent" -> "httpUserAgent",
    "Via" -> "httpVia",
    "X-Forwarded-For" -> "httpXForwardedFor",
    "X-Requested-With" -> "httpXRequestedWith")

  private val responseHeaderMdcKeys = Map(
    "Cache-Control" -> "httpCacheControl",
    "Content-Length" -> "httpContentLength",
    "WWW-Authenticate" -> "httpWWWAuthenticate")
}

trait HttpMonitoring extends StrictLogging {
  import HttpMonitoring._

  def monitor[T](request: HttpServletRequest, response: HttpServletResponse)(func: => T) = {
    val timestamp = System.currentTimeMillis
    MDC.put("timestamp", timestamp.toString)
    MDC.put("facilityVersion", JarManifest.blinkboxDefault.flatMap(_.implementationVersion).getOrElse("???"))
    MDC.put("httpMethod", request.getMethod)
    MDC.put("httpPath", request.getPathInfo)
    MDC.put("httpPathAndQuery", request.getPathInfo + Option(request.getQueryString).map(q => s"?$q").getOrElse(""))
    requestHeaderMdcKeys.foreach {
      case (name, key) => Option(request.getHeader(name)).foreach(MDC.put(key, _))
    }
    MDC.put("httpClientIP", request.getRemoteAddr)

    val result = func
    val duration = System.currentTimeMillis - timestamp
    MDC.put("httpStatus", response.getStatus.toString)
    // TODO: response headers
    MDC.put("httpApplicationTime", duration.toString)

    val message = s"${request.getMethod} ${request.getPathInfo} returned ${response.getStatus} in ${duration}ms"
    if (response.getStatus >= 500) logger.error(message)
    else if (response.getStatus >= 400 && response.getStatus != 401) logger.warn(message)
    else logger.info(message)

    result
  }

}
