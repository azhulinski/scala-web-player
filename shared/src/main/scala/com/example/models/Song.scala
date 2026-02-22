package com.example.models

import io.circe.{Decoder, Encoder, HCursor, Json}

final case class Song(
                       name: String,
                       file: String,
                       duration: Option[Int] = None,
                       title: Option[String] = None,
                       artist: Option[String] = None,
                       album: Option[String] = None,
                     ) derives CanEqual

object Song:
  given Encoder[Song] with
    def apply(song: Song): Json =
      Json.obj(
        "name" -> Json.fromString(song.name),
        "file" -> Json.fromString(song.file),
        "duration" -> song.duration.fold(Json.Null)(Json.fromInt),
        "title" -> song.title.fold(Json.Null)(Json.fromString),
        "artist" -> song.artist.fold(Json.Null)(Json.fromString),
        "album" -> song.album.fold(Json.Null)(Json.fromString),
      )

  given Decoder[Song] with
    def apply(c: HCursor): Decoder.Result[Song] =
      for
        name <- c.get[String]("name")
        file <- c.get[String]("file")
        duration <- c.get[Option[Int]]("duration")
        title <- c.get[Option[String]]("title")
        artist <- c.get[Option[String]]("artist")
        album <- c.get[Option[String]]("album")
      yield Song(name, file, duration, title, artist, album)


