import sbtassembly.AssemblyPlugin.autoImport.*
import sbtassembly.MergeStrategy

ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.8.1"
ThisBuild / organization := "com.example"

val http4sVersion = "1.0.0-M46"
val catsEffectVersion = "3.6.3"
val circeVersion = "0.14.15"

lazy val shared = project
  .in(file("shared"))
  .settings(
    name := "mp3-player-shared",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
    )
  )

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
      "net.jthink" % "jaudiotagger" % "3.0.1"
    ),
    
    Compile / resourceGenerators += Def.task {
      val log = streams.value.log

      val repoRoot = (ThisBuild / baseDirectory).value
      val frontendDist = repoRoot / "frontend" / "dist"
      val outDir = (Compile / resourceManaged).value / "public"

      if (frontendDist.exists()) {
        IO.delete(outDir)
        IO.createDirectory(outDir)
        IO.copyDirectory(frontendDist, outDir)
        (outDir ** "*").get
      } else {
        log.warn(s"frontend/dist not found at: $frontendDist (run: cd frontend && npm run build)")
        Seq.empty[java.io.File]
      }
    }.taskValue,
    
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



