import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import spray.revolver.RevolverPlugin._


object Build extends Build {
  val sprayVersion = "1.3.1"
  val akkaVersion = "2.3.2"

  lazy val root = Project("javabin-rest-on-akka", file("."))
    .settings(
      name := "javabin-rest-on-akka",
      organization := "org.nkvoll",
      version := "1.0.0-SNAPSHOT",

      scalaVersion := "2.10.4",

      unmanagedJars in Compile += file("lib/sigar/sigar-1.6.4.jar"),
      javaOptions in Runtime += "-Djava.library.path=lib/sigar",

      resolvers ++= Seq(
        "spray repo" at "http://repo.spray.io/"
      ),
      scalacOptions := Seq(
        "-encoding", "utf8",
        "-feature",
        "-unchecked",
        "-deprecation",
        "-target:jvm-1.7",
        "-language:_",
        "-Xlog-reflective-calls"
      )
    ).settings(Revolver.settings: _*).settings(formatSettings: _*).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster" % akkaVersion,

        "com.typesafe" % "config" % "1.2.1",
        "org.clapper" %% "grizzled-slf4j" % "1.0.2",
        "ch.qos.logback" % "logback-classic" % "1.1.2",

        "io.spray" % "spray-routing" % sprayVersion,
        "io.spray" % "spray-can" % sprayVersion,
        "io.spray" % "spray-client" % sprayVersion,
        "io.spray" % "spray-http" % sprayVersion,
        "io.spray" %% "spray-json" % "1.2.6",

        "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.3",
        "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.3.3",
        "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % "2.3.3",

        "nl.grons" %% "metrics-scala" % "3.2.0",
        "com.codahale.metrics" % "metrics-jvm" % "3.0.2",
        "com.codahale.metrics" % "metrics-logback" % "3.0.2",
        "org.slf4j" % "log4j-over-slf4j" % "1.7.7",

        "org.elasticsearch" % "elasticsearch" % "1.1.1",
        "joda-time" % "joda-time" % "2.3",
        "org.joda" % "joda-convert" % "1.6",
        "org.mindrot" % "jbcrypt" % "0.3m",

        // used by the config/hosted.conf example:
        "no.found.elasticsearch" % "elasticsearch-transport-module" % "0.8.7-1.0.0",

        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
        "io.spray" % "spray-testkit" % sprayVersion % "test",
        "org.scalatest" % "scalatest_2.10" % "2.1.6" % "test"
    ))

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  import scalariform.formatter.preferences._

  def formattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, false)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
}
