package io.septimalmind.magen.importer

import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.{Chord, Modifier, Platform}
import io.septimalmind.magen.util.{DefaultPaths, PathExpander}

import java.io.BufferedInputStream
import java.nio.file.{Path, Paths}
import java.util.zip.ZipFile
import scala.util.Using
import scala.xml.{Elem, XML}

case class IdeaKeymapInfo(
  name: String,
  parent: String,
  source: Path,
  bundled: Boolean,
)

object IdeaSchemeImporter {
  def listKeymaps(): List[IdeaKeymapInfo] = {
    val userKeymaps = listUserKeymaps()
    val bundledKeymaps = listBundledKeymaps()
    (userKeymaps ++ bundledKeymaps).sortBy(_.name)
  }

  def importFrom(keymapId: String, keymapSources: List[Path]): ImportedScheme = {
    val allKeymaps = keymapSources.flatMap(discoverKeymapsIn)
    val keymap = allKeymaps
      .find(_.name == keymapId)
      .getOrElse {
        val available = allKeymaps.map(_.name).mkString(", ")
        throw new RuntimeException(s"Keymap '$keymapId' not found. Available: $available")
      }

    val xml = loadKeymapXml(keymap)
    parseKeymapXml(xml)
  }

  def importFromFile(file: Path): ImportedScheme = {
    val xml = XML.loadFile(file.toFile)
    parseKeymapXml(xml)
  }

  private def parseKeymapXml(xml: Elem): ImportedScheme = {
    val actions = xml \ "action"
    val bindings = actions.flatMap {
      action =>
        val actionId = (action \ "@id").text
        val shortcuts = action \ "keyboard-shortcut"

        shortcuts.flatMap {
          shortcut =>
            val firstKeystroke = (shortcut \ "@first-keystroke").text
            val secondKeystroke = (shortcut \ "@second-keystroke").text

            if (firstKeystroke.nonEmpty) {
              val firstCombo = parseIdeaCombo(firstKeystroke)
              val combos = if (secondKeystroke.nonEmpty) {
                List(firstCombo, parseIdeaCombo(secondKeystroke))
              } else {
                List(firstCombo)
              }
              Some(ImportedBinding(actionId, Chord(combos), List.empty))
            } else {
              None
            }
        }
    }

    ImportedScheme(ImportSource.Idea, bindings.toList)
  }

  // IntelliJ format: "ctrl shift K" or "ctrl alt BACK_SPACE"
  private def parseIdeaCombo(keystroke: String): KeyCombo = {
    val parts = keystroke.split("\\s+").toList
    val (modParts, keyParts) = parts.partition(isModifier)

    val modifiers = modParts.map {
      case s if s.toLowerCase == "ctrl" || s.toLowerCase == "control" => Modifier.Ctrl
      case s if s.toLowerCase == "alt" => Modifier.Alt
      case s if s.toLowerCase == "shift" => Modifier.Shift
      case s if s.toLowerCase == "meta" => Modifier.Meta
      case other => throw new RuntimeException(s"Unknown modifier: $other")
    }

    assert(keyParts.size == 1, s"Expected exactly one key in '$keystroke', got: $keyParts")
    val key = normalizeIdeaKey(keyParts.head)
    KeyCombo(modifiers, key)
  }

  private def isModifier(s: String): Boolean = {
    val lower = s.toLowerCase
    lower == "ctrl" || lower == "control" || lower == "alt" || lower == "shift" || lower == "meta"
  }

  private def normalizeIdeaKey(key: String): NamedKey = {
    key.toUpperCase match {
      case "BACK_SPACE" => NamedKey("Backspace")
      case "PAGE_DOWN" => NamedKey("PageDown")
      case "PAGE_UP" => NamedKey("PageUp")
      case "OPEN_BRACKET" => NamedKey("BracketLeft")
      case "CLOSE_BRACKET" => NamedKey("BracketRight")
      case "MINUS" => NamedKey("Minus")
      case "EQUALS" => NamedKey("Equal")
      case "COMMA" => NamedKey("Comma")
      case "PERIOD" => NamedKey("Period")
      case "SEMICOLON" => NamedKey("Semicolon")
      case "SLASH" | "DIVIDE" => NamedKey("Slash")
      case "BACK_SLASH" => NamedKey("Backslash")
      case "AMPERSAND" => NamedKey("Quote")
      case "BACK_QUOTE" => NamedKey("Backquote")
      case "MULTIPLY" => NamedKey("MULTIPLY")
      case s if s.startsWith("NUMPAD") => NamedKey(s)
      case "ENTER" => NamedKey("enter")
      case "ESCAPE" => NamedKey("escape")
      case "TAB" => NamedKey("tab")
      case "SPACE" => NamedKey("space")
      case "DELETE" => NamedKey("delete")
      case "HOME" => NamedKey("home")
      case "END" => NamedKey("end")
      case "UP" => NamedKey("up")
      case "DOWN" => NamedKey("down")
      case "LEFT" => NamedKey("left")
      case "RIGHT" => NamedKey("right")
      case "F1" => NamedKey("F1")
      case "F2" => NamedKey("F2")
      case "F3" => NamedKey("F3")
      case "F4" => NamedKey("F4")
      case "F5" => NamedKey("F5")
      case "F6" => NamedKey("F6")
      case "F7" => NamedKey("F7")
      case "F8" => NamedKey("F8")
      case "F9" => NamedKey("F9")
      case "F10" => NamedKey("F10")
      case "F11" => NamedKey("F11")
      case "F12" => NamedKey("F12")
      case s if s.length == 1 => NamedKey(s)
      case other => NamedKey(other)
    }
  }

  // -- Keymap discovery --

  private def listUserKeymaps(): List[IdeaKeymapInfo] = {
    val patterns = DefaultPaths.ideaUserKeymapPatterns(Platform.detect())
    val paths = PathExpander.expandGlobs(patterns)
    paths.flatMap(parseKeymapFileInfo)
  }

  private def listBundledKeymaps(): List[IdeaKeymapInfo] = {
    val idePaths = discoverIdePaths()
    idePaths.flatMap {
      idePath =>
        val jarPath = findAppClientJar(idePath)
        jarPath.toList.flatMap(listKeymapsInJar)
    }
  }

  private def discoverKeymapsIn(source: Path): List[IdeaKeymapInfo] = {
    if (source.toFile.isDirectory) {
      // user keymaps directory
      val xmlFiles = source.toFile.listFiles().filter(_.getName.endsWith(".xml"))
      xmlFiles.flatMap(f => parseKeymapFileInfo(f.toPath)).toList
    } else if (source.toString.endsWith(".jar")) {
      listKeymapsInJar(source)
    } else if (source.toString.endsWith(".xml")) {
      parseKeymapFileInfo(source).toList
    } else {
      List.empty
    }
  }

  private def parseKeymapFileInfo(path: Path): Option[IdeaKeymapInfo] = {
    try {
      val xml = XML.loadFile(path.toFile)
      val name = (xml \ "@name").text
      val parent = (xml \ "@parent").text
      Some(IdeaKeymapInfo(name, parent, path, bundled = false))
    } catch {
      case _: Exception => None
    }
  }

  private def loadKeymapXml(info: IdeaKeymapInfo): Elem = {
    if (info.bundled) {
      loadKeymapFromJar(info)
    } else {
      XML.loadFile(info.source.toFile)
    }
  }

  private def loadKeymapFromJar(info: IdeaKeymapInfo): Elem = {
    Using(new ZipFile(info.source.toFile)) {
      zip =>
        val entries = scala.jdk.CollectionConverters.EnumerationHasAsScala(zip.entries()).asScala
        val keymapEntry = entries.find {
          e =>
            e.getName.startsWith("keymaps/") && e.getName.endsWith(".xml") && {
              Using(new BufferedInputStream(zip.getInputStream(e))) {
                is =>
                  val xml = XML.load(is)
                  (xml \ "@name").text == info.name
              }.getOrElse(false)
            }
        }

        keymapEntry match {
          case Some(entry) =>
            Using(new BufferedInputStream(zip.getInputStream(entry))) {
              is =>
                XML.load(is)
            }.get
          case None =>
            throw new RuntimeException(s"Keymap '${info.name}' not found in JAR ${info.source}")
        }
    }.get
  }

  private def listKeymapsInJar(jarPath: Path): List[IdeaKeymapInfo] = {
    try {
      Using(new ZipFile(jarPath.toFile)) {
        zip =>
          val entries = scala.jdk.CollectionConverters.EnumerationHasAsScala(zip.entries()).asScala
          entries
            .filter(e => e.getName.startsWith("keymaps/") && e.getName.endsWith(".xml")).flatMap {
              entry =>
                try {
                  Using(new BufferedInputStream(zip.getInputStream(entry))) {
                    is =>
                      val xml = XML.load(is)
                      val name = (xml \ "@name").text
                      val parent = (xml \ "@parent").text
                      IdeaKeymapInfo(name, parent, jarPath, bundled = true)
                  }.toOption
                } catch {
                  case _: Exception => None
                }
            }.toList
      }.getOrElse(List.empty)
    } catch {
      case _: Exception => List.empty
    }
  }

  private def discoverIdePaths(): List[Path] = {
    val hostPlatform = Platform.detect()
    val patterns = DefaultPaths.ideaInstallationPatterns(hostPlatform)

    val fromPath = List("idea-ultimate", "idea-community", "rider").flatMap {
      cmd =>
        try {
          val proc = new ProcessBuilder("which", cmd).start()
          val result = new String(proc.getInputStream.readAllBytes()).trim
          proc.waitFor()
          if (proc.exitValue() == 0 && result.nonEmpty) {
            val resolved = Paths.get(result).toRealPath()
            Some(resolved.getParent.getParent)
          } else None
        } catch {
          case _: Exception => None
        }
    }

    val fromGlobs = PathExpander.expandGlobs(patterns).filter(_.toFile.isDirectory)
    (fromPath ++ fromGlobs).distinct
  }

  private def findAppClientJar(idePath: Path): Option[Path] = {
    val libDir = idePath.resolve("lib")
    if (libDir.toFile.isDirectory) {
      val jar = libDir.resolve("app-client.jar")
      if (jar.toFile.exists()) Some(jar) else None
    } else {
      None
    }
  }
}
