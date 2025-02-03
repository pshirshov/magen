package io.septimalmind.magen

import io.septimalmind.magen.Key.{KeyCombo, NamedKey}

import scala.xml.PrettyPrinter

object IdeaRenderer extends Renderer {
  // useful: https://github.com/JetBrains/intellij-community/blob/ed982b658a0688970a7773fbb81fce3723ab5416/plugins/ide-startup/importSettings/src/com/intellij/ide/startup/importSettings/transfer/backend/providers/vscode/mappings/KeyBindingsMappings.kt#L115
  override def id: String = "idea.xml"

  override def render(mapping: Mapping): String = {
    val out = mapping.mapping.map { c =>
      val a = c.mappings.find(_.target == "vscode").head
      <action id={a.action}>
        {format(ShortcutParser.parseUnsafe(c.binding))}
      </action>
    }

    val full = <keymap version="1" name="Magen" parent="Empty">
      {out}
    </keymap>
    val pp = new PrettyPrinter(120, 2)
    val prettyXml: String = pp.format(full)
    prettyXml
  }

  private def format(binding: List[KeyCombo]) = {
    binding match {
      case f :: s :: Nil
          if f.modifiers == s.modifiers && f.modifiers.size == 1 =>
        val fs = renderCombo(f)
        val ss = renderCombo(s)
        val ssShort = renderCombo(s.dropMods)

        Seq(
          <keyboard-shortcut first-keystroke={fs} second-keystroke={ss} />,
          <keyboard-shortcut first-keystroke={fs} second-keystroke={ssShort} />
        )

      case f :: s :: Nil =>
        val fs = renderCombo(f)
        val ss = renderCombo(s)
        Seq(<keyboard-shortcut first-keystroke={fs} second-keystroke={ss} />)

      case o =>
        println(o)
        ???
    }
  }

  private def renderCombo(f: KeyCombo): String = {
    val modsStr = f.modifiers.map {
      case Modifier.Ctrl  => "ctrl"
      case Modifier.Alt   => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta  => "meta"
    }

    (modsStr :+ renderKey(f.key)).mkString(" ").toLowerCase
  }

  private def renderKey(f: NamedKey): String = {
    camelToUnderscore(f.name)
  }

  def camelToUnderscore(s: String): String = {
    s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase
  }

//  fun shortcutMap(shortcut: String): String = when (shortcut) {
//    "SHIFT" -> "shift"
//    "ALT" -> "alt"
//    "CMD" -> "meta"
//    "CTRL" -> "ctrl"
//    "-" -> "MINUS"
//    "=" -> "EQUALS"
//    "BACKSPACE" -> "BACK_SPACE"
//    "," -> "COMMA"
//    ";" -> "SEMICOLON"
//    "." -> "PERIOD"
//    "/" -> "SLASH"
//    "\\" -> "BACK_SLASH"
//    "PAGEDOWN" -> "PAGE_DOWN"
//    "PAGEUP" -> "PAGE_UP"
//    "[" -> "OPEN_BRACKET"
//    "]" -> "CLOSE_BRACKET"
//    "'" -> "AMPERSAND"
//    else -> shortcut
//  }
}
