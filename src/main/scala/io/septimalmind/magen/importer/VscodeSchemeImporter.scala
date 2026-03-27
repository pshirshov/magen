package io.septimalmind.magen.importer

import io.circe.*
import io.septimalmind.magen.model.Chord
import io.septimalmind.magen.model.Key.NamedKey
import io.septimalmind.magen.util.{Aliases, ShortcutParser}
import izumi.fundamentals.platform.files.IzFiles

import java.nio.file.Path

object VscodeSchemeImporter {
  def importFrom(source: Path): ImportedScheme = {
    val content = IzFiles.readString(source)
    val entries = parser
      .parse(content)
      .flatMap(_.as[List[JsonObject]])
      .getOrElse(throw new RuntimeException(s"Failed to parse VSCode keybindings from $source"))

    val bindings = entries.flatMap {
      obj =>
        val command = obj("command").flatMap(_.asString)
        val key     = obj("key").flatMap(_.asString)
        val when    = obj("when").flatMap(_.asString)

        (command, key) match {
          case (Some(cmd), Some(k)) if !cmd.startsWith("-") =>
            val chord      = ShortcutParser.parseUnsafe(k)
            val normalized = normalizeChord(chord)

            // deduplicate aliases — keep only the longest (most explicit) form
            val extended = Aliases.extend(normalized)
            if (extended.size > 1) {
              // this chord has aliases; only keep it if it's the canonical (longest) form
              val rendered     = extended.map(SchemeWriter.renderChord)
              val thisRendered = SchemeWriter.renderChord(normalized)
              if (thisRendered == rendered.maxBy(_.length)) {
                Some(ImportedBinding(cmd, normalized, when.toList))
              } else {
                None
              }
            } else {
              Some(ImportedBinding(cmd, normalized, when.toList))
            }

          case _ => None
        }
    }

    ImportedScheme(ImportSource.VSCode, bindings)
  }

  // VSCode uses characters like "[" for BracketLeft — normalize to internal names
  private def normalizeChord(chord: Chord): Chord = {
    Chord(chord.combos.map {
      combo =>
        combo.copy(key = normalizeKey(combo.key))
    })
  }

  private val vscodeToInternal: Map[String, String] = Map(
    "["  -> "BracketLeft",
    "]"  -> "BracketRight",
    "/"  -> "Slash",
    "\\" -> "Backslash",
    "-"  -> "Minus",
    "="  -> "Equal",
    "'"  -> "Quote",
    "`"  -> "Backquote",
    ";"  -> "Semicolon",
    ","  -> "Comma",
    "."  -> "Period",
  )

  private def normalizeKey(key: NamedKey): NamedKey = {
    vscodeToInternal.get(key.name) match {
      case Some(internal) => NamedKey(internal)
      case None           => key
    }
  }
}
