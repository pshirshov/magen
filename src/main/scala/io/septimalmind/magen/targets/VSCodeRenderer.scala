package io.septimalmind.magen.targets

import io.circe.*
import io.circe.syntax.*
import io.septimalmind.magen.Renderer
import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.*
import io.septimalmind.magen.util.Aliases
import izumi.fundamentals.platform.files.IzFiles

import java.nio.file.Paths

object VSCodeRenderer extends Renderer {
  override def id: String = "vscode.json"

  override def render(mapping: Mapping): String = {
    val mappings = for {
      c <- mapping.mapping
      a <- c.vscode.toList
      b <- (c.binding ++ a.binding).toList.flatMap(Aliases.extend)
    } yield {
      format(a, b)
    }

    val full = (unbind() ++ mappings.flatten).asJson
    full.printWith(Printer.spaces2)
  }

  private def format(a: VSCodeAction, binding: Chord): Seq[JsonObject] = {
    val combo = binding.combos.map(renderCombo).mkString(" ")

    val main = JsonObject(
      "key" -> Json.fromString(combo),
      "command" -> Json.fromString(a.action),
    )

    if (a.context.nonEmpty) {
      a.context.map {
        ctx =>
          main.deepMerge(JsonObject("when" -> Json.fromString(ctx)))
      }
    } else {
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
    f.name match {
      case "BracketLeft" => "["
      case "BracketRight" => "]"
      case "Slash" => "/"
      case "Backslash" => "\\"
      case "Minus" => "-"
      case "Equal" => "="
      case "Quote" => "'"
      case "Backquote" => "`"
      case "Semicolon" => ";"
      case "Comma" => ","
      case "Period" => "."
      case s if s.length == 1 => s.toLowerCase
      case s => s.toLowerCase
    }
  }

  def unbind(): List[JsonObject] = {
    val negations = List(
      "mappings/vscode/vscode-keymap-linux-!negate-all.json",
      "mappings/vscode/vscode-keymap-linux-!negate-continue.json",
      "mappings/vscode/vscode-keymap-linux-!negate-gitlens.json",
    )

    negations.flatMap {
      n =>
        val fa = IzFiles.readString(Paths.get(n))
        val pa = parser.parse(fa)
        pa.flatMap(_.as[List[JsonObject]]).toOption.get
    }

  }
}
