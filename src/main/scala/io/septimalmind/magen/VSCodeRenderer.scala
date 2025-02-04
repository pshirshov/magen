package io.septimalmind.magen

import io.circe.*
import io.circe.syntax.*
import io.septimalmind.magen.IdeaRenderer.{format, renderCombo, shortcutMap}
import io.septimalmind.magen.Key.{KeyCombo, NamedKey}

object VSCodeRenderer extends Renderer {
  override def id: String = "vscode.json"

  override def render(mapping: Mapping): String = {
    val mappings = for {
      c <- mapping.mapping
      a <- c.vscode.toList
    } yield {
      format(a, ShortcutParser.parseUnsafe(c.binding))
    }

    val full = mappings.flatten.asJson
    full.printWith(Printer.spaces2)
  }

  private def format(a: VSCodeAction, binding: List[KeyCombo]): Seq[JsonObject] = {
    val combo = binding.map(renderCombo).mkString(" ")

    val main = JsonObject(
      "key" -> Json.fromString(combo),
      "command" -> Json.fromString(a.action),
    )

    val bindings = binding match {
      case f :: s :: Nil if f.modifiers == s.modifiers && f.modifiers.size == 1 =>
        val fs = renderCombo(f)
        val ssShort = renderCombo(s.dropMods)

        Seq(
          main,
          JsonObject(
            "key" -> Json.fromString(List(fs, ssShort).mkString(" ")),
            "command" -> Json.fromString(a.action),
          ),
        )

      case o =>
        Seq(main)
    }

    a.context match {
      case Some(value) =>
        value.flatMap {
          ctx =>
            bindings.map(o => o.deepMerge(JsonObject("when" -> Json.fromString(ctx))))
        }

      case None =>
        bindings
    }
  }

  private def renderCombo(f: KeyCombo): String = {
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
