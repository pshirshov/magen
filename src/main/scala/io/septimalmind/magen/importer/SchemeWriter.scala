package io.septimalmind.magen.importer

import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.{Chord, Modifier, SchemeId}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

object SchemeWriter {
  def write(schemeId: SchemeId, imported: ImportedScheme): Path = {
    val schemeDir = Paths.get("mappings", "schemes", schemeId.name)
    Files.createDirectories(schemeDir)

    val grouped = imported.bindings
      .groupBy(b => b.action)
      .toList
      .sortBy(_._1)

    val sb = new StringBuilder()
    sb.append("mapping:\n")

    grouped.foreach {
      case (action, bindings) =>
        val allChords = bindings.map(_.chord).distinct
        val allContexts = bindings.flatMap(_.context).distinct

        sb.append(s"""  - id: "$action"\n""")
        sb.append("    binding:\n")
        allChords.foreach { chord =>
          sb.append(s"""      - "${renderChord(chord)}"\n""")
        }

        imported.source match {
          case ImportSource.VSCode =>
            sb.append("    vscode:\n")
            sb.append(s"""      action: "$action"\n""")
            if (allContexts.nonEmpty) {
              sb.append(s"""      context: [${allContexts.map(c => s""""$c"""").mkString(", ")}]\n""")
            }
            sb.append("    idea:\n")
            sb.append("      missing: true\n")
            sb.append("    zed:\n")
            sb.append("      missing: true\n")

          case ImportSource.Idea =>
            sb.append("    idea:\n")
            sb.append(s"""      action: "$action"\n""")
            sb.append("    vscode:\n")
            sb.append("      missing: true\n")
            sb.append("    zed:\n")
            sb.append("      missing: true\n")

          case ImportSource.Zed =>
            sb.append("    zed:\n")
            sb.append(s"""      action: "$action"\n""")
            if (allContexts.nonEmpty) {
              sb.append(s"""      context: [${allContexts.map(c => s""""$c"""").mkString(", ")}]\n""")
            }
            sb.append("    idea:\n")
            sb.append("      missing: true\n")
            sb.append("    vscode:\n")
            sb.append("      missing: true\n")
        }
    }

    val outputFile = schemeDir.resolve("imported.yaml")
    Files.write(outputFile, sb.toString().getBytes(StandardCharsets.UTF_8))
    println(s"Wrote ${grouped.size} bindings to $outputFile")
    outputFile
  }

  def renderChord(chord: Chord): String = {
    chord.combos.map(renderCombo).mkString(" ")
  }

  private def renderCombo(combo: KeyCombo): String = {
    val modsStr = combo.modifiers.map {
      case Modifier.Ctrl => "ctrl"
      case Modifier.Alt => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta => "meta"
    }

    (modsStr :+ renderKey(combo.key)).mkString("+")
  }

  private def renderKey(key: NamedKey): String = {
    key.name match {
      case s if s.length == 1 && s.head.isLetter => s"[Key${s.toUpperCase}]"
      case s if s.length == 1 && s.head.isDigit  => s
      case s                                      => s"[$s]"
    }
  }
}
