package io.septimalmind.magen.config

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class InstallerPaths(
  vscode: List[String],
  idea: List[String],
  zed: List[String],
)

object InstallerPaths {
  implicit val encoder: Encoder[InstallerPaths] = deriveEncoder
  implicit val decoder: Decoder[InstallerPaths] = deriveDecoder

  val empty: InstallerPaths = InstallerPaths(
    vscode = List.empty,
    idea = List.empty,
    zed = List.empty,
  )
}

case class MagenConfig(
  `installer-paths`: InstallerPaths
)

object MagenConfig {
  implicit val encoder: Encoder[MagenConfig] = deriveEncoder
  implicit val decoder: Decoder[MagenConfig] = deriveDecoder

  val empty: MagenConfig = MagenConfig(
    `installer-paths` = InstallerPaths.empty
  )
}
