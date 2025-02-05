package io.septimalmind.magen.targets

import io.circe.*
import io.circe.syntax.*
import io.septimalmind.magen.model.*
import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.util.{Aliases, ShortcutParser}
import io.septimalmind.magen.{Mapping, Renderer}

object VSCodeRenderer extends Renderer {
  override def id: String = "vscode.json"

  override def render(mapping: Mapping): String = {
    val mappings = for {
      c <- mapping.mapping
      a <- c.vscode.toList
      b <- c.binding.map(ShortcutParser.parseUnsafe).flatMap(Aliases.extend)
    } yield {
      format(a, b)
    }

    val full = mappings.flatten.asJson
    full.printWith(Printer.spaces2)
  }

  private def format(a: VSCodeAction, binding: Chord): Seq[JsonObject] = {
    val combo = binding.combos.map(renderCombo).mkString(" ")

    val main = JsonObject(
      "key" -> Json.fromString(combo),
      "command" -> Json.fromString(a.action),
    )

    a.context match {
      case Some(value) =>
        value.map {
          ctx =>
            main.deepMerge(JsonObject("when" -> Json.fromString(ctx)))
        }

      case None =>
        Seq(main)
    }
  }

  def renderChord(c: Chord): String = {
    c.combos.map(renderCombo).mkString(" ")
  }
  
  def renderCombo(f: KeyCombo): String = {
    val modsStr = f.modifiers.map {
      case Modifier.Ctrl => "ctrl"
      case Modifier.Alt => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta => "meta"
    }

    (modsStr :+ renderKey(f.key)).mkString("+")
  }

  private def renderKey(f: NamedKey): String = {
    if (f.name.length == 1) {
      s"[Key${f.name.toUpperCase}]"
    } else {
      f.name
    }
  }

}
