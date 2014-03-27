# scala-resource-server

An experimental implementation of a resource server written in Scala

## Build & Run ##

```sh
$ sbt
> container:start
```

## Running tests

You must insure you have libpng and imagemagick installed, then you can follow the usual bundle install and cucumber procedure:

```sh
$ brew install libpng imagemagick
$ bundle
$ cucumber
``
