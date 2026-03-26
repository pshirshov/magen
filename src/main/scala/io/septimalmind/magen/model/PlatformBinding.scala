package io.septimalmind.magen.model

import io.circe.Decoder

sealed trait PlatformBinding {
  def resolve(platform: Platform): List[String]
}

object PlatformBinding {
  case class Universal(bindings: List[String]) extends PlatformBinding {
    def resolve(platform: Platform): List[String] = bindings
  }

  case class PerPlatform(
    default: Option[List[String]],
    macos: Option[List[String]],
    linux: Option[List[String]],
    win: Option[List[String]],
  ) extends PlatformBinding {
    def resolve(platform: Platform): List[String] = {
      val specific = platform match {
        case Platform.MacOS => macos
        case Platform.Linux => linux
        case Platform.Win   => win
      }
      specific.orElse(default).getOrElse(List.empty)
    }
  }

  private val stringOrListDecoder: Decoder[List[String]] =
    Decoder[String].map(List(_)).or(Decoder[List[String]])

  implicit val decoder: Decoder[PlatformBinding] = Decoder.instance { cursor =>
    cursor.as[String].map(s => Universal(List(s))).orElse {
      cursor.as[List[String]].map(Universal.apply).orElse {
        for {
          default <- cursor.downField("default").as[Option[List[String]]](Decoder.decodeOption(stringOrListDecoder))
          macos   <- cursor.downField("macos").as[Option[List[String]]](Decoder.decodeOption(stringOrListDecoder))
          linux   <- cursor.downField("linux").as[Option[List[String]]](Decoder.decodeOption(stringOrListDecoder))
          win     <- cursor.downField("win").as[Option[List[String]]](Decoder.decodeOption(stringOrListDecoder))
        } yield PerPlatform(default, macos, linux, win)
      }
    }
  }
}
