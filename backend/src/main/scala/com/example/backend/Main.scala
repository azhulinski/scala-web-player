package com.example.backend

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.{Host, Port}
import com.example.models.Song
import com.example.models.Song.given
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import cats.syntax.semigroupk.*

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object Main extends IOApp.Simple:

  // Configure with env var MUSIC_BASE, e.g. C:\Users\<you>\Music
  private val BASE_PATH: String =
    sys.env.getOrElse("MUSIC_BASE", "/home/andrii")

  private val basePath: Path =
    Paths.get(BASE_PATH).toAbsolutePath.normalize()

  given loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  /**
   * Accepts either:
   *  - a relative path (treated as relative to basePath), or
   *  - an absolute path
   *    Returns a normalized absolute path only if it is inside basePath.
   */
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

  /**
   * Recursively finds all .mp3 files in a directory.
   * NOTE: Song.path is returned as absolute path (keeps your current frontend working).
   */
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
              Song(
                name = filePath.getFileName.toString,
                path = filePath.toAbsolutePath.normalize().toString,
                duration = None
              )
            }
            .sortBy(_.name.toLowerCase)
      catch
        case _: Exception => List.empty
    }

  private def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // List folders under base or under a subfolder: /folders?dir=<relative>
    case req@GET -> Root / "folders" =>
      val dirParam: String = req.uri.query.params.getOrElse("dir", "")
      validatePath(dirParam) match
        case Some(dirAbs) =>
          for
            folders <- listSubFolders(dirAbs)
            json = folders.map { case (name, relPath) =>
              Json.obj(
                "name" -> Json.fromString(name),
                "path" -> Json.fromString(relPath) // relative to basePath
              )
            }.asJson
            resp <- Ok(json)
          yield resp
        case None =>
          Forbidden(Json.obj("error" -> Json.fromString("Access denied")))

    // List mp3 files (recursive) in a folder: /list?dir=<relative>
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

    // Stream mp3 file: /stream?file=<absolute or relative>
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
                  .handleErrorWith { e =>
                    InternalServerError(
                      Json.obj("error" -> Json.fromString(s"Streaming failed: ${e.getMessage}"))
                    )
                  }
            case None =>
              Forbidden(Json.obj("error" -> Json.fromString("Access denied")))
        case None =>
          BadRequest(Json.obj("error" -> Json.fromString("Missing 'file' parameter")))
  }

  // CORS middleware (Vite proxy also works; this keeps direct access workable too)
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

  // Serve static files from resources/public (frontend dist)
  private def staticRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> path =>
      val filePath = "/" + path.segments.map(_.encoded).mkString("/")
      val resource = s"public$filePath"
      
      // Try to load the file from resources
      IO(Option(this.getClass.getClassLoader.getResourceAsStream(resource))).flatMap {
        case Some(stream) =>
          try
            val content = stream.readAllBytes()
            stream.close()
            
            // Determine content type
            val contentType = filePath match
              case p if p.endsWith(".html") => "text/html"
              case p if p.endsWith(".js") => "application/javascript"
              case p if p.endsWith(".css") => "text/css"
              case p if p.endsWith(".json") => "application/json"
              case p if p.endsWith(".svg") => "image/svg+xml"
              case p if p.endsWith(".png") => "image/png"
              case p if p.endsWith(".jpg") || p.endsWith(".jpeg") => "image/jpeg"
              case _ => "application/octet-stream"
            
            Ok(content)
              .map(_.putHeaders(Header.Raw(ci"Content-Type", contentType)))
          catch
            case _: Exception => 
              // Fall back to index.html for SPA routing
              fallbackToIndex()
        case None =>
          // Fall back to index.html for SPA routing
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
    // Combine routes with proper priority: API routes first, then static files
    val combined = routes <+> staticRoutes
    
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(8080).get)
      .withHttpApp(corsMiddleware(combined).orNotFound)
      .build
      .useForever
