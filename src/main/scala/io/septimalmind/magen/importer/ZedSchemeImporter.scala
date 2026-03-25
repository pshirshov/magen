package io.septimalmind.magen.importer

import io.circe.*
import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.{Chord, Modifier}
import izumi.fundamentals.platform.files.IzFiles

import java.nio.file.Path

object ZedSchemeImporter {
  def importFrom(source: Path): ImportedScheme = {
    val content = IzFiles.readString(source)
    val json = parser.parse(content)
      .getOrElse(throw new RuntimeException(s"Failed to parse Zed keymap from $source"))

    val entries = json.asArray.getOrElse(throw new RuntimeException(s"Expected JSON array in $source"))

    val bindings = entries.flatMap { entry =>
      val obj = entry.asObject.getOrElse(throw new RuntimeException("Expected JSON object in keymap array"))
      val context = obj("context").flatMap(_.asString).toList
      val bindingsObj = obj("bindings").flatMap(_.asObject)
        .getOrElse(throw new RuntimeException("Missing 'bindings' field in keymap entry"))

      bindingsObj.toList.flatMap {
        case (key, actionJson) =>
          val action = actionJson.asString
            .getOrElse(actionJson.noSpaces) // some actions may be objects
          val chord = parseZedKey(key)
          chord.map(c => ImportedBinding(action, c, context))
      }
    }

    ImportedScheme(ImportSource.Zed, bindings.toList)
  }

  private def parseZedKey(key: String): Option[Chord] = {
    // Zed format: "ctrl-shift-k" or "ctrl-k ctrl-shift-v" (space-separated for chords)
    val comboParts = key.split("\\s+").toList
    val combos = comboParts.map(parseZedCombo)
    if (combos.forall(_.isDefined)) {
      Some(Chord(combos.flatten))
    } else {
      None
    }
  }

  private def parseZedCombo(combo: String): Option[KeyCombo] = {
    // "ctrl-shift-k" -> modifiers + key
    // tricky: "-" is both separator and could be a key
    val parts = combo.split("-").toList
    if (parts.isEmpty) return None

    val (modParts, keyParts) = parts.partition(isModifier)
    if (keyParts.isEmpty) return None

    val modifiers = modParts.map(parseModifier)
    // rejoin remaining parts with "-" in case the key itself contains "-"
    val keyStr = keyParts.mkString("-")
    val key = normalizeZedKey(keyStr)
    Some(KeyCombo(modifiers, key))
  }

  private def isModifier(s: String): Boolean = {
    s == "ctrl" || s == "alt" || s == "shift" || s == "cmd"
  }

  private def parseModifier(s: String): Modifier = {
    s match {
      case "ctrl" => Modifier.Ctrl
      case "alt" => Modifier.Alt
      case "shift" => Modifier.Shift
      case "cmd" => Modifier.Meta
      case other => throw new RuntimeException(s"Unknown Zed modifier: $other")
    }
  }

  private val zedToInternal: Map[String, String] = Map(
    "[" -> "BracketLeft",
    "]" -> "BracketRight",
    "/" -> "Slash",
    "\\" -> "Backslash",
    "'" -> "Quote",
    "`" -> "Backquote",
    ";" -> "Semicolon",
    "," -> "Comma",
    "." -> "Period",
    "=" -> "Equal",
  )

  private def normalizeZedKey(key: String): NamedKey = {
    zedToInternal.get(key) match {
      case Some(internal) => NamedKey(internal)
      case None =>
        key match {
          case s if s.length == 1 && s.head.isLetter => NamedKey(s.toUpperCase)
          case s if s.length == 1 && s.head.isDigit => NamedKey(s)
          // Zed uses lowercase names: "escape", "enter", "tab", etc.
          case s => NamedKey(s)
        }
    }
  }
}
