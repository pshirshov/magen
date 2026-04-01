package io.septimalmind.magen.targets

import io.circe.*
import io.circe.syntax.*
import io.septimalmind.magen.Renderer
import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.*
import io.septimalmind.magen.util.Aliases

object ZedRenderer extends Renderer {
  override def id: String = "zed.json"

  override def render(mapping: Mapping, platform: Platform): String = {
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
            "context"  -> Json.fromString(contexts.mkString(" || ")),
            "bindings" -> bindingsMap.asJson,
          )
        }
    }

    // Prepend essential global UI bindings for menus and pickers
    val globalBindings       = createGlobalBindings()
    val pickerBindings       = createPickerBindings()
    val pickerEditorBindings = createPickerEditorBindings()

    val allBindings = List(globalBindings, pickerBindings, pickerEditorBindings) ++ result

    Json.arr(allBindings.map(_.asJson): _*).printWith(Printer.spaces2)
  }

  private def createGlobalBindings(): JsonObject = {
    // These bindings apply globally and are essential for basic UI interaction
    val bindings = Map(
      "enter"     -> Json.fromString("menu::Confirm"),
      "escape"    -> Json.fromString("menu::Cancel"),
      "ctrl-c"    -> Json.fromString("menu::Cancel"),
      "tab"       -> Json.fromString("menu::SelectNext"),
      "shift-tab" -> Json.fromString("menu::SelectPrevious"),
      "up"        -> Json.fromString("menu::SelectPrevious"),
      "down"      -> Json.fromString("menu::SelectNext"),
      "ctrl-n"    -> Json.fromString("menu::SelectNext"),
      "ctrl-p"    -> Json.fromString("menu::SelectPrevious"),
    )
    JsonObject("bindings" -> bindings.asJson)
  }

  private def createPickerBindings(): JsonObject = {
    // Picker-specific context to ensure up/down work correctly in pickers
    val bindings = Map(
      "up"   -> Json.fromString("menu::SelectPrevious"),
      "down" -> Json.fromString("menu::SelectNext"),
    )
    JsonObject(
      "context"  -> Json.fromString("Picker || menu"),
      "bindings" -> bindings.asJson,
    )
  }

  private def createPickerEditorBindings(): JsonObject = {
    // When editing in a picker (e.g., typing in search), escape should cancel the picker
    val bindings = Map(
      "escape" -> Json.fromString("menu::Cancel"),
      "up"     -> Json.fromString("menu::SelectPrevious"),
      "down"   -> Json.fromString("menu::SelectNext"),
    )
    JsonObject(
      "context"  -> Json.fromString("Picker > Editor"),
      "bindings" -> bindings.asJson,
    )
  }

  case class ZedBinding(context: List[String], key: String, action: Json)

  private def format(a: ZedAction, binding: Chord): ZedBinding = {
    val combo = renderChord(binding)

    val actionJson = a.args match {
      case Some(args) =>
        Json.arr(Json.fromString(a.action), args)
      case None =>
        Json.fromString(a.action)
    }

    ZedBinding(a.context, combo, actionJson)
  }

  def renderChord(c: Chord): String = {
    c.combos.map(renderCombo).mkString(" ")
  }

  def renderCombo(f: KeyCombo): String = {
    val modsStr = f.modifiers.map {
      case Modifier.Ctrl  => "ctrl"
      case Modifier.Alt   => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta  => "cmd"
    }

    val keyStr = renderKey(f.key)

    (modsStr :+ keyStr).mkString("-")
  }

  private def renderKey(f: NamedKey): String = {
    f.name match {
      // Special keys that need exact Zed naming
      case "escape"                      => "escape"
      case "enter"                       => "enter"
      case "tab"                         => "tab"
      case "backspace"                   => "backspace"
      case "delete"                      => "delete"
      case "home"                        => "home"
      case "end"                         => "end"
      case "pageup"                      => "pageup"
      case "pagedown"                    => "pagedown"
      case "left" | "ArrowLeft"          => "left"
      case "right" | "ArrowRight"        => "right"
      case "up" | "ArrowUp"              => "up"
      case "down" | "ArrowDown"          => "down"
      case "space"                       => "space"
      case "BracketLeft"                 => "["
      case "BracketRight"                => "]"
      case "Slash" | "DIVIDE"            => "/"
      case "Backslash" | "IntlBackslash" => "\\"
      case "Minus"                       => "-"
      case "Equal"                       => "="
      case "Quote"                       => "'"
      case "Backquote" | "BACK_QUOTE"    => "`"
      case "Semicolon"                   => ";"
      case "Comma"                       => ","
      case "Period"                      => "."
      case "MULTIPLY"                    => "*"
      case s if s.startsWith("NUMPAD")   => s.drop(6).toLowerCase
      // Single character keys
      case s if s.length == 1 => s.toLowerCase
      case s                  => s.toLowerCase
    }
  }
}
