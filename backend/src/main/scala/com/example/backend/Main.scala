package com.example.backend

import cats.effect.{IO, IOApp}
import cats.syntax.semigroupk.*
import com.comcast.ip4s.{Host, Port}
import com.example.models.Song
import com.example.models.Song.given
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object Main extends IOApp.Simple:

  private val BASE_PATH: String =
    sys.env.getOrElse("MUSIC_BASE", "/home/andrii")

  private val basePath: Path =
    Paths.get(BASE_PATH).toAbsolutePath.normalize()

  given loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  /** Resolve input (relative or absolute) and ensure it stays under basePath */
  private def validatePath(input: String): Option[Path] =
    val raw = Paths.get(input)
    val resolved =
      if raw.isAbsolute then raw.toAbsolutePath.normalize()
      else basePath.resolve(raw).normalize()
    if resolved.startsWith(basePath) then Some(resolved) else None

  /**
   * Lists immediate subfolders of a directory.
   * Returns (folderName, relativePathFromBase).
   */
  private def listSubFolders(dirAbs: Path): IO[List[(String, String)]] =
    IO.blocking {
      try
        if !Files.isDirectory(dirAbs) then List.empty
        else
          Files.list(dirAbs)
            .iterator()
            .asScala
            .toList
            .filter(Files.isDirectory(_))
            .map { p =>
              val name = p.getFileName.toString
              val rel = basePath.relativize(p.toAbsolutePath.normalize()).toString
              (name, rel)
            }
            .sortBy(_._1.toLowerCase)
      catch
        case _: Exception => List.empty
    }

  private def readTags(fileAbs: Path): (Option[String], Option[String], Option[String], Option[Int]) =
    try
      val audioFile = AudioFileIO.read(fileAbs.toFile)
      val tag = Option(audioFile.getTag)
      val header = Option(audioFile.getAudioHeader)

      val title = tag.map(_.getFirst(FieldKey.TITLE)).map(_.trim).filter(_.nonEmpty)
      val artist = tag.map(_.getFirst(FieldKey.ARTIST)).map(_.trim).filter(_.nonEmpty)
      val album = tag.map(_.getFirst(FieldKey.ALBUM)).map(_.trim).filter(_.nonEmpty)
      val durationSec = header.map(_.getTrackLength).filter(_ > 0)

      (title, artist, album, durationSec)
    catch
      case _: Exception => (None, None, None, None)

  private def findMp3Files(dirAbs: Path): IO[List[Song]] =
    IO.blocking {
      try
        if !Files.isDirectory(dirAbs) then List.empty
        else
          Files.walk(dirAbs)
            .iterator()
            .asScala
            .toList
            .filter(p => Files.isRegularFile(p) && p.toString.toLowerCase.endsWith(".mp3"))
            .map { filePath =>
              val abs = filePath.toAbsolutePath.normalize()
              val rel = basePath.relativize(abs).toString // <- key: relative id for streaming
              val (title, artist, album, durationSec) = readTags(abs)
              val fileName = abs.getFileName.toString

              Song(
                name = title.getOrElse(fileName),
                file = rel,
                duration = durationSec,
                title = title,
                artist = artist,
                album = album
              )
            }
            .sortBy(_.name.toLowerCase)
      catch
        case _: Exception => List.empty
    }

  private def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@GET -> Root / "folders" =>
      val dirParam: String = req.uri.query.params.getOrElse("dir", "")
      validatePath(dirParam) match
        case Some(dirAbs) =>
          for
            folders <- listSubFolders(dirAbs)
            json = folders.map { case (name, relPath) =>
              Json.obj(
                "name" -> Json.fromString(name),
                "path" -> Json.fromString(relPath)
              )
            }.asJson
            resp <- Ok(json)
          yield resp
        case None =>
          Forbidden(Json.obj("error" -> Json.fromString("Access denied")))

    case req@GET -> Root / "list" =>
      val dirParam: String = req.uri.query.params.getOrElse("dir", "")
      validatePath(dirParam) match
        case Some(dirAbs) =>
          for
            songs <- findMp3Files(dirAbs)
            resp <- Ok(songs.asJson)
          yield resp
        case None =>
          Forbidden(Json.obj("error" -> Json.fromString("Access denied")))

    case req@GET -> Root / "stream" =>
      req.uri.query.params.get("file") match
        case Some(fileParam) =>
          validatePath(fileParam) match
            case Some(fileAbs) =>
              if !Files.exists(fileAbs) || !Files.isRegularFile(fileAbs) then
                NotFound()
              else
                StaticFile
                  .fromPath(fs2.io.file.Path.fromNioPath(fileAbs), Some(req))
                  .getOrElseF(NotFound())
                  .map(
                    _.putHeaders(
                      Header.Raw(ci"Content-Type", "audio/mpeg"),
                      Header.Raw(ci"Accept-Ranges", "bytes"),
                    )
                  )
            case None =>
              Forbidden(Json.obj("error" -> Json.fromString("Access denied")))
        case None =>
          BadRequest(Json.obj("error" -> Json.fromString("Missing 'file' parameter")))
  }

  // CORS middleware
  private def corsMiddleware(http: HttpRoutes[IO]): HttpRoutes[IO] =
    HttpRoutes { req =>
      http.run(req).map { resp =>
        resp.putHeaders(
          Header.Raw(ci"Access-Control-Allow-Origin", "*"),
          Header.Raw(ci"Access-Control-Allow-Methods", "GET, OPTIONS"),
          Header.Raw(ci"Access-Control-Allow-Headers", "Content-Type"),
        )
      }
    }

  private def staticRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root =>
      fallbackToIndex()

    case GET -> path =>
      val filePath = path.segments.map(_.encoded).mkString("/")
      val resource = s"public/$filePath"

      IO(Option(this.getClass.getClassLoader.getResourceAsStream(resource))).flatMap {
        case Some(stream) =>
          try
            val content = stream.readAllBytes()
            stream.close()

            val contentType = filePath match
              case p if p.endsWith(".html") => "text/html"
              case p if p.endsWith(".js") => "application/javascript"
              case p if p.endsWith(".css") => "text/css"
              case p if p.endsWith(".json") => "application/json"
              case p if p.endsWith(".svg") => "image/svg+xml"
              case p if p.endsWith(".png") => "image/png"
              case p if p.endsWith(".jpg") || p.endsWith(".jpeg") => "image/jpeg"
              case _ => "application/octet-stream"

            Ok(content).map(_.putHeaders(Header.Raw(ci"Content-Type", contentType)))
          catch
            case _: Exception =>
              fallbackToIndex()
        case None =>
          fallbackToIndex()
      }
  }

  private def fallbackToIndex(): IO[Response[IO]] =
    IO(Option(this.getClass.getClassLoader.getResourceAsStream("public/index.html"))).flatMap {
      case Some(stream) =>
        try
          val content = stream.readAllBytes()
          stream.close()
          Ok(content)
            .map(_.putHeaders(Header.Raw(ci"Content-Type", "text/html")))
        catch
          case _: Exception => NotFound()
      case None => NotFound()
    }

  def run: IO[Unit] =
    val combined = routes <+> staticRoutes

    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(8080).get)
      .withHttpApp(corsMiddleware(combined).orNotFound)
      .build
      .useForever
