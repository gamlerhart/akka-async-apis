import sbt._
import Keys._


object AkkaAsyncModules extends Build  {

  import Dependencies._

  lazy val buildSettings = Seq(
    organization := "info.gamlor.akkaasync",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.9.1"
  )

  lazy val root = Project("akka-async-modules", file(".")) aggregate(akkaExperiment)

  lazy val akkaExperiment: Project = Project(
    id = "akka-io",
    base = file("./akka-io"),
    settings = defaultSettings ++ Seq(
      unmanagedBase <<= baseDirectory {
        base => base / "lib"
      },
      libraryDependencies ++= Seq(akkaActors, scalaTest, akkaTestKit, mockito)
    ))

  override lazy val settings = super.settings ++ buildSettings

  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Tools-Repo" at "http://scala-tools.org/repo-releases/",

    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked","-optimize"),
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7", "-Xlint:deprecation"),
    // show full stack traces
    testOptions in Test += Tests.Argument("-oF")
  )


}

object Dependencies {

  val scalaTest = "org.scalatest" %% "scalatest" % "1.6.1" % "test"
  val netty = "org.jboss.netty" % "netty" % "3.2.5.Final"
  val asyncHttp = "com.ning" % "async-http-client" % "1.6.5"

  val akkaTestKit = "com.typesafe.akka" % "akka-testkit" % "2.0-RC2" % "test"
  val akkaActors = "com.typesafe.akka" % "akka-actor" % "2.0-RC2"
  val akkaRemoteActors = "se.scalablesolutions.akka" % "akka-remote" % "1.2"
  val mockito = "org.mockito" % "mockito-core" % "1.9.0-rc1" % "test"

  val h2Database = "com.h2database" % "h2" % "1.3.161"
  val scalaQuery = "org.scalaquery" % "scalaquery_2.9.0-1" % "0.9.5"
}
