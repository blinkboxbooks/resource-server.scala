import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import javax.servlet.ServletContext
import scala.concurrent.duration._
import org.scalatra.LifeCycle
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logging
import com.blinkboxbooks.resourceserver.ResourceServlet
import com.blinkboxbooks.resourceserver.TimeLoggingThresholds

class ScalatraBootstrap extends LifeCycle with Logging {

  override def init(context: ServletContext) {

    // Get config options.
    val config = ConfigFactory.load("resource-server")

    val dataDirStr = config.getString("data_dir")
    val dataDirectory = FileSystems.getDefault().getPath(dataDirStr)
    if (!Files.isDirectory(dataDirectory)) {
      throw new ConfigException.BadPath(dataDirStr, "Data directory parameter must point to a valid directory")
    }

    val tmpDirectory = if (config.hasPath("tmp.directory")) Some(new File(config.getString("tmp.directory"))) else None
    if (tmpDirectory.isDefined && !tmpDirectory.get.isDirectory()) {
      throw new ConfigException.BadPath(dataDirStr, "tmp directory parameter must point to a valid directory")
    }

    val numThreads = if (config.hasPath("threads.count"))
      config.getInt("threads.count")
    else
      Runtime.getRuntime().availableProcessors()
    logger.info(s"Using $numThreads threads for image processing")

    val infoThreshold = Duration(config.getInt("logging.perf.threshold.info"), MILLISECONDS)
    val warnThreshold = Duration(config.getInt("logging.perf.threshold.warn"), MILLISECONDS)
    val errorThreshold = Duration(config.getInt("logging.perf.threshold.error"), MILLISECONDS)

    // Create and mount the resource servlet.
    context.mount(ResourceServlet(dataDirectory, tmpDirectory, infoThreshold, warnThreshold, errorThreshold, numThreads), "/*")
  }

}