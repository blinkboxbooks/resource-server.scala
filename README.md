# scala-resource-server

An experimental implementation of a resource server written in Scala

## Installation ##

To install the resource server on a host, you need to perform the following steps:

Get a copy of the JAR file that contains the version you want to install, called resource-server-assembly-n.n.n.jar.

Create a configuration for the service by putting a properties file called "resource-server.properties" in the same location as the JAR file.
There's [an example configuration here](src/main/resources/resource-server.properties.example) that you can use as a starting point.
The following properties **must** be set:

* data_dir (REQUIRED): This specifies the root directory that contains the files to be served up by the service.
* cache.directory (REQUIRED): This specifies a directory that will contain a cache of smaller versions of images.
* resource.server.port (REQUIRED): This specifies the port number on which the embedded web server listens.

Additionally, you may want to set the following properties:

* tmp.directory (OPTIONAL): This names the directory that will contain temporary files used internally by the service. If not set, this will
default to the system tmp directory.
* threads.count (OPTIONAL): This is the number of threads that will be used for handling image transformation requests (requests for images 
that specify a specific format, resolution or quality). If not set, this will default to the number of CPUs reported by the JVM 
on which the service runs.
* cache.threads.count (OPTIONAL): This is the number of threads that will be used for handling background jobs for storing smaller versions
of accessed images. If not set, this will default to 0. A value of 0 means that generation of cached images is disabled.
* resource.server.path (REQUIRED): This is the base path of the servlet that handles resource requests. Should be left as "/"
in normal cases.

## Build & Run ##

Pre-requisite: Make sure you have sbt and Java 7 installed on your system and on your path.

### Running in development ###

Copy the config file src/main/resources/resource-server.properties.example to a file called resource-server.properties
and override the properties for your local system. In particular, set the 'data_dir' property to point at the root
directory to serve up files from.

The run it using the commands:

```
$ sbt run
```

Or, for automatic restart on change while developing, use:

```
$ sbt
> ~re-start
```

### Building a standalone jar file ###

Run the command:

```
$ sbt assembly
```

This will build a file resource-server-assembly-n.n.n.jar which contains everything you need to run a server. To run it, first
make sure you have a properties file as described above in the same directory as the jar file, then use the command:

```
$ java -jar resource-server-assembly-n.n.n.jar
```

## Running functional tests

You must insure you have libpng and imagemagick installed, then you can follow the usual bundle install and cucumber procedure:

```
$ brew install libpng imagemagick
$ bundle
$ cucumber
```

## Running performance tests

The performance tests use Gatling 2, which at the time of writing is not released yet. The latest snapshot can be downloaded
from [the Sonatype repository](https://oss.sonatype.org/content/repositories/snapshots/io/gatling/highcharts/gatling-charts-highcharts/2.0.0-SNAPSHOT/). (These tests will not run with Gatling 1)

### Test setup ###

In `bin/gatling.sh` (in your Gatling installation), set the following if you have enough memory:

```
-Xms10G -Xmx10G -Xmn1G
```

Also, in `conf/gatling.conf`, set the following:

```
percentile1 = 50				# in percent
percentile2 = 90				# in percent
allowPoolingConnection = false	# allow pooling HTTP connections (keep-alive header automatically added)
connectionTimeout = 1000		# timeout when establishing a connection
```

Edit the test properties files for your environment (e.g. to point at the resource server you want to test). These are located in `<baldrick home>/performance-tests/images/`.

* Rename the from `*.properties.example` to `*.properties`, to create the following files:
  * ImageScenarios.properties
  * ImageSimulations.properties
* Set the resource server document root to point to the `<baldrick root>/performance-test/images/test-files` directory.
* Run the script `test-files/duplicate.sh' that copies the big-0000.png and epub files 10,000 times.

To include checking of the content of returned images using MD5 hashes:

* Edit the ImageScenarios.scala to include or exclude the MD5 response size check (this is commented out by default).

The setup above describes how to run the performance tests on a set on well known input files. You can also run some of the tests against an arbitrary set of test files, such as a selection of images and ePub files from the real system. In this case, you have to disable the MD5 checks, and the ePub file tests, as these currently only work with well-known input data.


### Running Gatling tests ###

To run tests from a 'clean' state, that takes into account work done on image background processing:

* Stop the resource server.
* Delete any cached files (in the directory set in the `cache.directory` property in the resource server config.
* Start the resource server.

If testing performance of the resource server given an already populated cache of resized images, run the tests to completion first, then restart the test *without* clearing the cache directory.

Run tests by executing:

```
<gatling2_home>/bin/gatling.sh -sf <baldrick home>/performance-tests/images/ -s com.blinkboxbooks.resourceserver.ResourceServerSimulation
```

You can also add an alias into your .profile/.bash_profile if running on Linux or OS/X:

```
alias grun='<gatling2 home>/bin/gatling.sh'
```

Or,  do environment or PATH variable updates, whatever is easiest.

When the tests have completed, Gatling will generate a report containing the results.

