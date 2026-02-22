import sbtassembly.AssemblyPlugin.autoImport.*
import sbtassembly.MergeStrategy

ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.8.1"
ThisBuild / organization := "com.example"

val http4sVersion = "1.0.0-M46"
val catsEffectVersion = "3.6.3"
val circeVersion = "0.14.15"

// Shared module - common models
lazy val shared = project
  .in(file("shared"))
  .settings(
    name := "mp3-player-shared",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
    )
  )

// Backend module - http4s server
lazy val backend = project
  .in(file("backend"))
  .dependsOn(shared)
  .settings(
    name := "mp3-player-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.typelevel" %% "log4cats-core" % "2.7.1",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
      "org.slf4j" % "slf4j-simple" % "2.0.17",
    ),
    assembly / assemblyJarName := "mp3-player-backend.jar",
    assembly / mainClass := Some("com.example.backend.Main"),
    assembly / assemblyMergeStrategy := {
      case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )

lazy val root = project
  .in(file("."))
  .aggregate(shared, backend)
  .settings(
    name := "mp3-player",
    publish / skip := true,
  )



