import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.servlet.ServletContext
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import org.scalatra.LifeCycle
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logging
import com.blinkboxbooks.resourceserver._
import java.util.concurrent.ThreadFactory
import org.apache.commons.lang3.concurrent.BasicThreadFactory

class ScalatraBootstrap extends LifeCycle with Logging {

  override def init(context: ServletContext) {

    // Get config options.
    val config = ConfigFactory.load("resource-server")

    // Root directory of resources to serve up.
    val dataDirStr = config.getString("data_dir")
    val dataDirectory = FileSystems.getDefault().getPath(dataDirStr)
    if (!Files.isDirectory(dataDirectory)) {
      throw new ConfigException.BadPath(dataDirStr, "Data directory parameter must point to a valid directory")
    }

    // Cache directory, where smaller versions of image files are stored.
    val cacheDirectory = FileSystems.getDefault().getPath(config.getString("cache.directory"))
    if (!Files.isDirectory(cacheDirectory)) {
      throw new ConfigException.BadPath("cache.directory", "Cache directory parameter must point to a valid directory")
    }

    // Maximum number of image processing threads.
    val numThreads = if (config.hasPath("threads.count"))
      config.getInt("threads.count")
    else
      Runtime.getRuntime().availableProcessors()
    logger.info(s"Using $numThreads threads for image processing")

    // Logging levels.
    val infoThreshold = Duration(config.getInt("logging.perf.threshold.info"), MILLISECONDS)
    val warnThreshold = Duration(config.getInt("logging.perf.threshold.warn"), MILLISECONDS)
    val errorThreshold = Duration(config.getInt("logging.perf.threshold.error"), MILLISECONDS)

    // Not making this configurable at the moment, as this should only change after careful consideration!
    val cachedFileSizes = Set(400, 900)

    val cacheingThreadCount =
      if (config.hasPath("cache.threads.count")) config.getInt("cache.threads.count") else Runtime.getRuntime().availableProcessors()
    val threadFactory = new BasicThreadFactory.Builder().namingPattern("image-caching-%d").priority(Thread.MIN_PRIORITY).build()
    val cacheingExecutionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(cacheingThreadCount, threadFactory))

    val fileResolver = new EpubEnabledFileResolver(dataDirectory)
    // Create and mount the resource servlet.
    context.mount(ResourceServlet(fileResolver,
      new FileSystemImageCache(cacheDirectory, cachedFileSizes, fileResolver), cacheingExecutionContext, numThreads,
      infoThreshold, warnThreshold, errorThreshold), "/*")
  }

}
