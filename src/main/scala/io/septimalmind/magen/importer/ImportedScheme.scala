package io.septimalmind.magen.importer

import io.septimalmind.magen.model.Chord

sealed trait ImportSource

object ImportSource {
  case object VSCode extends ImportSource
  case object Idea extends ImportSource
  case object Zed extends ImportSource
}

case class ImportedBinding(
  action: String,
  chord: Chord,
  context: List[String],
)

case class ImportedScheme(
  source: ImportSource,
  bindings: List[ImportedBinding],
)
