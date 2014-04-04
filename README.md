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
of accessed images. If not set, this will default to the number of CPUs reported by the JVM 
on which the service runs.
* resource.server.path (REQUIRED): This is the base path of the servlet that handles resource requests. Should be left as "/"
in normal cases.

## Build & Run ##

Pre-requisite: Make sure you have sbt and Java 7 installed on your system and on your path.

### Running in development ###

Copy the config file src/main/resources/resource-server.properties.example to a file called resource-server.properties
and override the properties for your local system. In particular, set the 'data_dir' property to point at the root
directory to serve up files from.

```sh
$ sbt
> container:start
```

### Building a standalone jar file ###

```sh
$ sbt assembly
```

This will build a file resource-server-assembly-n.n.n.jar which contains everything you need to run a server. To run it, first
make sure you have a properties file as described above in the same directory as the jar file, then use the command:

```sh
$ java -jar resource-server-assembly-n.n.n.jar
```

## Running tests

You must insure you have libpng and imagemagick installed, then you can follow the usual bundle install and cucumber procedure:

```sh
$ brew install libpng imagemagick
$ bundle
$ cucumber
```
