package com.example.models

import io.circe.{Decoder, Encoder, HCursor, Json}

/**
 * Represents an MP3 file in the music library
 *
 * @param name     File name (e.g., "song.mp3")
 * @param path     Full absolute path to the file
 * @param duration Duration in seconds (optional)
 */
final class Song(
                  val name: String,
                  val path: String,
                  val duration: Option[Int]
                ) derives CanEqual:
  def copy(
            name: String = this.name,
            path: String = this.path,
            duration: Option[Int] = this.duration
          ): Song = new Song(name, path, duration)

  override def toString: String = s"Song($name, $path, $duration)"

  override def hashCode(): Int =
    (name.hashCode * 31 + path.hashCode) * 31 + duration.hashCode

  override def equals(obj: Any): Boolean = obj match
    case s: Song => s.name == name && s.path == path && s.duration == duration
    case _ => false

object Song:
  def apply(name: String, path: String, duration: Option[Int] = None): Song =
    new Song(name, path, duration)

  def unapply(s: Song): Option[(String, String, Option[Int])] =
    Some((s.name, s.path, s.duration))

  given Encoder[Song] with
    def apply(song: Song): Json =
      Json.obj(
        "name" -> Json.fromString(song.name),
        "path" -> Json.fromString(song.path),
        "duration" -> song.duration.fold(Json.Null)(Json.fromInt)
      )

  given Decoder[Song] with
    def apply(c: HCursor): Decoder.Result[Song] =
      for
        name <- c.get[String]("name")
        path <- c.get[String]("path")
        duration <- c.get[Option[Int]]("duration")
      yield Song(name, path, duration)


