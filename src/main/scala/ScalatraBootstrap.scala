import java.nio.file.FileSystems
import java.nio.file.Files

import org.scalatra.LifeCycle

import com.blinkboxbooks.resourceserver.ResourceServlet
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory

import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {

    // Get config options.
    val config = ConfigFactory.load("resource-server")
    val rootDirStr = config.getString("root.directory")
    val rootDirectory = FileSystems.getDefault().getPath(rootDirStr)
    if (!Files.isDirectory(rootDirectory)) {
      throw new ConfigException.BadPath(rootDirStr, "Root directory parameter must point to a valid directory")
    }
    val tmpDirectory = if (config.hasPath("tmp.directory")) Some(config.getString("tmp.directory")) else None

    // Create and mount the resource servlet.
    context.mount(ResourceServlet(rootDirectory, tmpDirectory), "/*")
  }

}
