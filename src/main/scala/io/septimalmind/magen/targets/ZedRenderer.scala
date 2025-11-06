package io.septimalmind.magen.targets

import io.circe.*
import io.circe.syntax.*
import io.septimalmind.magen.Renderer
import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.*
import io.septimalmind.magen.util.Aliases

object ZedRenderer extends Renderer {
  override def id: String = "zed.json"

  override def render(mapping: Mapping): String = {
    val bindings = for {
      c <- mapping.mapping
      a <- c.zed.toList
      b <- c.binding.toList.flatMap(Aliases.extend)
    } yield {
      format(a, b)
    }

    val grouped = bindings.groupBy(_.context).toList.sortBy(_._1.mkString(","))

    val result = grouped.map {
      case (contexts, entries) =>
        val bindingsMap = entries.map(e => (e.key, e.action)).toMap
        if (contexts.isEmpty) {
          JsonObject("bindings" -> bindingsMap.asJson)
        } else {
          JsonObject(
            "context" -> Json.fromString(contexts.mkString(" && ")),
            "bindings" -> bindingsMap.asJson,
          )
        }
    }

    Json.arr(result.map(_.asJson): _*).printWith(Printer.spaces2)
  }

  case class ZedBinding(context: List[String], key: String, action: Json)

  private def format(a: ZedAction, binding: Chord): ZedBinding = {
    val combo = renderChord(binding)

    val actionJson = a.action match {
      case s if s.contains("::") && !s.contains(" ") =>
        // Simple action without parameters
        Json.fromString(s)
      case s =>
        // Action that might need parameters - for now treat as string
        Json.fromString(s)
    }

    ZedBinding(a.context, combo, actionJson)
  }

  def renderChord(c: Chord): String = {
    c.combos.map(renderCombo).mkString(" ")
  }

  def renderCombo(f: KeyCombo): String = {
    val modsStr = f.modifiers.map {
      case Modifier.Ctrl => "ctrl"
      case Modifier.Alt => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta => "cmd"
    }

    val keyStr = renderKey(f.key)

    (modsStr :+ keyStr).mkString("-")
  }

  private def renderKey(f: NamedKey): String = {
    f.name match {
      // Special keys that need exact Zed naming
      case "escape" => "escape"
      case "enter" => "enter"
      case "tab" => "tab"
      case "backspace" => "backspace"
      case "delete" => "delete"
      case "home" => "home"
      case "end" => "end"
      case "pageup" => "pageup"
      case "pagedown" => "pagedown"
      case "left" | "ArrowLeft" => "left"
      case "right" | "ArrowRight" => "right"
      case "up" | "ArrowUp" => "up"
      case "down" | "ArrowDown" => "down"
      case "space" => "space"
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
      // Single character keys
      case s if s.length == 1 => s.toLowerCase
      case s => s.toLowerCase
    }
  }
}
