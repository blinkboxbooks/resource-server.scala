package com.blinkboxbooks.resourceserver

import com.blinkbox.books.config.Configuration
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object JettyLauncher extends App with Configuration {
    val port = config.getInt("resource.server.port")
    val contextPath = config.getString("resource.server.path")

    val server = new Server(port)

    val context = new WebAppContext()
    context setContextPath(contextPath)
    context.setResourceBase(".")
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    server.setHandler(context)

    server.start()
    server.join()
}
