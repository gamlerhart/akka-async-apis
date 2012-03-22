import sbt._
import Keys._


object AkkaAsyncModules extends Build  {

  import Dependencies._

  lazy val buildSettings = Seq(
    organization := "info.gamlor.akkaasync",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.9.1"
  )

  lazy val root = Project("akka-async-modules", file(".")) aggregate(akkaIO, akkaWebClient)

  lazy val akkaIO: Project = Project(
    id = "akka-io",
    base = file("./akka-io"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Seq(akkaActors, scalaTest, akkaTestKit, mockito)
    ))
  lazy val akkaWebClient: Project = Project(
    id = "akka-webclient",
    base = file("./akka-webclient"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Seq(akkaActors,httpLib, scalaTest, akkaTestKit, mockito,simpleTestServer)
    )) dependsOn (akkaIO)



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

  val akkaTestKit = "com.typesafe.akka" % "akka-testkit" % "2.0" % "test"
  val akkaActors = "com.typesafe.akka" % "akka-actor" % "2.0"
  val mockito = "org.mockito" % "mockito-core" % "1.9.0-rc1" % "test"

  val httpLib = "com.ning" %"async-http-client"% "1.7.0"

  val simpleTestServer = "org.simpleframework" % "simple" % "4.1.21"  % "test"

}
