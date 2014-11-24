import org.scalatra.sbt._
import sbt.Keys._
import sbt._

object ResourceServerBuild extends Build {
  val Organization = "com.blinkboxbooks.platform.services"
  val Name = "resource-server"
  val Version = scala.io.Source.fromFile("VERSION").mkString.trim
  val ScalaVersion = "2.11.4"
  val ScalatraVersion = "2.3.0"

  lazy val project = Project(
    "resource-server",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      mainClass in (Compile, packageBin) := Some("com.blinkboxbooks.resourceserver.JettyLauncher"),
      publishArtifact in (Compile, packageDoc) := false, // Don’t publish bits we don't care about.
      publishArtifact in (Compile, packageSrc) := false,
      packageOptions in (Compile, packageBin) += Package.ManifestAttributes(java.util.jar.Attributes.Name.CLASS_PATH -> "."),
      libraryDependencies ++= Seq(
        "org.scalatra"              %% "scalatra"            % ScalatraVersion,
        "org.scalatra"              %% "scalatra-specs2"     % ScalatraVersion       % Test,
        "org.scalatra"              %% "scalatra-scalatest"  % ScalatraVersion       % Test,
        "org.eclipse.jetty"         %  "jetty-webapp"        % "9.2.5.v20141112"     % "container;compile",
        "org.eclipse.jetty.orbit"   %  "javax.servlet"       % "3.0.0.v201112011016" % "container;provided;test" artifacts Artifact("javax.servlet", "jar", "jar"),
        "commons-codec"             %  "commons-codec"       % "1.10",
        "com.mortennobel"           %  "java-image-scaling"  % "0.8.5",
        "junit"                     %  "junit"               % "4.11"                % Test,
        "org.scalatest"             %% "scalatest"           % "2.2.2"               % Test,
        "org.imgscalr"              %  "imgscalr-lib"        % "4.2",
        "org.apache.commons"        %  "commons-lang3"       % "3.3.2",
        "commons-collections"       %  "commons-collections" % "3.2.1",
        "commons-io"                %  "commons-io"          % "2.4",
        "com.google.jimfs"          %  "jimfs"               % "1.0"                 % Test,
        "com.jsuereth"              %% "scala-arm"           % "1.4",
        "com.blinkbox.books"        %% "common-lang"         % "0.2.1",
        "com.blinkbox.books"        %% "common-config"       % "2.0.1",
        "com.twelvemonkeys.common"  %  "common-lang"         % "3.0",
        "com.twelvemonkeys.imageio" %  "imageio-core"        % "3.0",
        "com.twelvemonkeys.imageio" %  "imageio-jpeg"        % "3.0"
      )
    )
  )
}
