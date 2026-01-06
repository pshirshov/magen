package io.septimalmind.magen.targets

import io.circe.parser
import io.septimalmind.magen.Renderer
import io.septimalmind.magen.model.*
import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.util.Aliases
import izumi.fundamentals.platform.files.IzFiles

import java.nio.file.Paths
import scala.annotation.tailrec
import scala.xml.PrettyPrinter

class IdeaRenderer(params: IdeaParams) extends Renderer {
  // useful:
  // https://github.com/JetBrains/intellij-community/blob/ed982b658a0688970a7773fbb81fce3723ab5416/plugins/ide-startup/importSettings/src/com/intellij/ide/startup/importSettings/transfer/backend/providers/vscode/mappings/KeyBindingsMappings.kt#L115
  // https://github.com/JetBrains/intellij-community/tree/b78007c38cd6213fe0cb5ca2cb6fddfd257dbc31/platform/platform-resources/src/keymaps

  // ❯ unzip -l /nix/store/g9hxdkvqp0001bqli059ap0jwvacdnsm-idea-ultimate-2024.3.2/idea-ultimate/lib/app-client.jar | grep default.xml
  //    46078  00-00-1980 00:00   keymaps/$default.xml

  // nix run nixpkgs#xmlstarlet -- sel -t -v '//action/@id' -n './junk/$default.xml'

  /*
   unzip -p "$(dirname $(readlink -f `which idea-ultimate`))/../idea-ultimate/lib/app-client.jar" 'keymaps/$default.xml' \
     | nix run nixpkgs#xmlstarlet -- sel -t -v '//action/@id' \
     | sort \
     | python -c "import sys,json; print(json.dumps([line.strip() for line in sys.stdin]))" | jq  > ./mappings/idea/idea-all-actions.json

   unzip -p "$(dirname $(readlink -f `which rider`))/../rider/lib/app-client.jar" 'keymaps/$default.xml' \
       | nix run nixpkgs#xmlstarlet -- sel -t -v '//action/@id' \
       | sort \
       | python -c "import sys,json; print(json.dumps([line.strip() for line in sys.stdin]))" | jq  > ./mappings/idea/rider-all-actions.json

   */
  override def id: String = "idea.xml"

  // TODO: index by commands/resolve dupes
  override def render(mapping: Mapping): String = {
    val index = (for {
      m <- mapping.mapping
      i <- m.idea.toList
    } yield {
      (i, m)
    }).groupBy(_._1.action)

    val defined = index.keySet
    val negations = IdeaRenderer.allIdeaActions().diff(defined).map(n => <action id={n} />)

    val mappings = index.view.map {
      case (a, pairs) =>
        val mouseBbs = pairs
          .filterNot(_._1.mouse.isEmpty)
          .flatMap {
            case (i, c) =>
              Seq(scala.xml.Comment(c.id)) ++ i.mouse.map {
                m =>
                  <mouse-shortcut keystroke={m}/>
              }

          }

        val bbs = pairs
          .flatMap {
            case (_, c) =>
              c.binding.map(b => (b, c.id))
          }
          .groupBy(_._1).map {
            case (chord, cs) =>
              Seq(scala.xml.Comment(cs.map(_._2).mkString(", "))) ++
              Aliases.extend(chord).map(renderChord)
          }
        <action id={a}>
          {bbs ++ mouseBbs}
        </action>
    } ++ (if (params.negate) negations else List.empty)

    val full = <keymap version="1" name="Magen" parent={params.parent}>
      {mappings}
    </keymap>
    val pp = new PrettyPrinter(120, 2)
    val prettyXml: String = pp.format(full)
    prettyXml
  }

  private def renderChord(binding: Chord) = {
    binding.combos match {
      case f :: s :: Nil =>
        val fs = renderCombo(f)
        val ss = renderCombo(s)
        Seq(<keyboard-shortcut first-keystroke={fs} second-keystroke={ss} />)
      case f :: Nil =>
        val fs = renderCombo(f)
        Seq(<keyboard-shortcut first-keystroke={fs} />)
      case o =>
        println(o)
        ???
    }
  }

  private def renderCombo(f: KeyCombo): String = {
    val modsStr = f.modifiers.map {
      case Modifier.Ctrl => "ctrl"
      case Modifier.Alt => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta => "meta"
    }

    (modsStr :+ renderKey(f.key)).mkString(" ").toLowerCase
  }

  private def renderKey(f: NamedKey): String = {
    shortcutMap(f.name)
  }

  @tailrec
  final def shortcutMap(shortcut: String): String = {
    if (shortcut.startsWith("[") && shortcut.endsWith("]")) {
      shortcutMap(shortcut.substring(1, shortcut.length - 1))
    } else {
      shortcut.toUpperCase match {
        case "SHIFT" => "shift"
        case "ALT" => "alt"
        case "CMD" => "meta"
        case "CTRL" => "ctrl"
        case "-" => "MINUS"
        case "=" => "EQUALS"
        case "BACKSPACE" => "BACK_SPACE"
        case "," => "COMMA"
        case ";" => "SEMICOLON"
        case "." => "PERIOD"
        case "/" => "SLASH"
        case "\\" => "BACK_SLASH"
        case "PAGEDOWN" => "PAGE_DOWN"
        case "PAGEUP" => "PAGE_UP"
        case "[" => "OPEN_BRACKET"
        case "]" => "CLOSE_BRACKET"
        case "'" => "AMPERSAND"
        case "BRACKETLEFT" => "OPEN_BRACKET"
        case "BRACKETRIGHT" => "CLOSE_BRACKET"
        case _ => shortcut
      }
    }
  }

}

object IdeaRenderer {
  def allIdeaActions(): Set[String] = {
    Seq(
      "./mappings/idea/idea-all-actions.json",
      "./mappings/idea/continue-all-actions.json",
    )
      .flatMap(readActionsFile)
      .toSet
  }

  private def readActionsFile(actionsFile: String) = {
    val fa = IzFiles.readString(Paths.get(actionsFile))
    val pa = parser.parse(fa)
    pa.flatMap(_.as[List[String]]).toOption.get.toSet
  }
}
