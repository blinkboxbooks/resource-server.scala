import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object ResourceServerBuild extends Build {
  val Organization = "com.blinkboxbooks.platform.services"
  val Name = "resource-server"
  val Version = scala.io.Source.fromFile("VERSION").mkString.trim
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.2"

  lazy val project = Project(
    "resource-server",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      credentials += Credentials(Path.userHome / ".sbt" / ".nexus"),
      publishTo := {
        val nexus = "http://jenkins:m0bJenk@nexus.mobcast.co.uk/"
        if (version.value.trim.endsWith("SNAPSHOT"))
          Some("Sonatype Nexus Repository Manager" at nexus + "nexus/content/repositories/snapshots/")
        else
          Some("Sonatype Nexus Repository Manager" at nexus + "nexus/content/repositories/releases")
      },
      mainClass in (Compile, packageBin) := Some("com.blinkboxbooks.resourceserver.JettyLauncher"),
      publishArtifact in (Compile, packageDoc) := false, // Don’t publish bits we don't care about.
      publishArtifact in (Compile, packageSrc) := false,
      packageOptions in (Compile, packageBin) += Package.ManifestAttributes(java.util.jar.Attributes.Name.CLASS_PATH -> "."),
      EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container;compile",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "com.typesafe" % "config" % "1.0.2",
        "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
        "org.slf4j"    % "slf4j-log4j12" % "1.7.5",
        "log4j"        % "log4j" % "1.2.17",
        "commons-codec" % "commons-codec" % "1.9",
        "joda-time"     % "joda-time" % "2.3",
        "junit"         % "junit" % "4.11" % "test",
        "org.scalatest" %% "scalatest" % "1.9.1" % "test",
        "org.imgscalr"  % "imgscalr-lib" % "4.2",
        "org.apache.commons" % "commons-lang3" % "3.3.1",
        "commons-collections" % "commons-collections" % "3.1",
        "commons-io" % "commons-io" % "2.4",
        "com.google.jimfs" % "jimfs" % "1.0-rc1" % "test",
        "com.jsuereth" % "scala-arm_2.10" % "1.3")))
}
