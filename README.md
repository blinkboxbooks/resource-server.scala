# scala-resource-server

An experimental implementation of a resource server written in Scala

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
