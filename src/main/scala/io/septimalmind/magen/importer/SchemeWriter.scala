package io.septimalmind.magen.importer

import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.{Chord, Modifier, SchemeId}
import io.septimalmind.magen.util.MagenPaths

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object SchemeWriter {
  def write(schemeId: SchemeId, imported: ImportedScheme): Path = {
    val schemeDir = MagenPaths.writableDir.resolve(schemeId.name)
    Files.createDirectories(schemeDir)

    val grouped = imported.bindings
      .groupBy(b => b.action)
      .toList
      .sortBy(_._1)

    var mapped   = 0
    var unmapped = 0

    val sb = new StringBuilder()
    sb.append("mapping:\n")

    grouped.foreach {
      case (action, bindings) =>
        val allChords   = bindings.map(_.chord).distinct
        val allContexts = bindings.flatMap(_.context).distinct

        sb.append(s"""  - id: "$action"\n""")
        sb.append("    binding:\n")
        allChords.foreach {
          chord =>
            sb.append(s"""      - "${renderChord(chord)}"\n""")
        }

        val equivalents = imported.source match {
          case ImportSource.Idea   => EditorMappings.lookupFromIdea(action)
          case ImportSource.VSCode => EditorMappings.lookupFromVscode(action, allContexts)
          case ImportSource.Zed    => EditorMappings.lookupFromZed(action, allContexts)
        }

        val hasMapped = writeEditorActions(sb, imported.source, action, allContexts, equivalents)
        if (hasMapped) mapped += 1 else unmapped += 1
    }

    val outputFile = schemeDir.resolve("imported.yaml")
    Files.write(outputFile, sb.toString().getBytes(StandardCharsets.UTF_8))
    println(s"Wrote ${grouped.size} bindings to $outputFile ($mapped with cross-editor mappings, $unmapped without)")
    outputFile
  }

  private def writeEditorActions(
    sb: StringBuilder,
    source: ImportSource,
    action: String,
    allContexts: List[String],
    equivalents: EditorEquivalents,
  ): Boolean = {
    source match {
      case ImportSource.Idea =>
        writeActionSection(sb, "idea", action, List.empty)
        val v = writeEquivalentOrMissing(sb, "vscode", equivalents.vscode)
        val z = writeEquivalentOrMissing(sb, "zed", equivalents.zed)
        v || z

      case ImportSource.VSCode =>
        writeActionSection(sb, "vscode", action, allContexts)
        val i = writeEquivalentOrMissing(sb, "idea", equivalents.idea)
        val z = writeEquivalentOrMissing(sb, "zed", equivalents.zed)
        i || z

      case ImportSource.Zed =>
        writeActionSection(sb, "zed", action, allContexts)
        val i = writeEquivalentOrMissing(sb, "idea", equivalents.idea)
        val v = writeEquivalentOrMissing(sb, "vscode", equivalents.vscode)
        i || v
    }
  }

  private def writeActionSection(sb: StringBuilder, editor: String, action: String, context: List[String]): Unit = {
    sb.append(s"    $editor:\n")
    sb.append(s"""      action: "$action"\n""")
    if (context.nonEmpty) {
      sb.append(s"""      context: [${context.map(c => s""""$c"""").mkString(", ")}]\n""")
    }
  }

  private def writeEquivalentOrMissing(sb: StringBuilder, editor: String, ref: Option[EditorActionRef]): Boolean = {
    ref match {
      case Some(r) =>
        sb.append(s"    $editor:\n")
        sb.append(s"""      action: "${r.action}"\n""")
        if (r.context.nonEmpty) {
          sb.append(s"""      context: [${r.context.map(c => s""""$c"""").mkString(", ")}]\n""")
        }
        true
      case None =>
        sb.append(s"    $editor:\n")
        sb.append("      missing: true\n")
        false
    }
  }

  def renderChord(chord: Chord): String = {
    chord.combos.map(renderCombo).mkString(" ")
  }

  private def renderCombo(combo: KeyCombo): String = {
    val modsStr = combo.modifiers.map {
      case Modifier.Ctrl  => "ctrl"
      case Modifier.Alt   => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta  => "meta"
    }

    (modsStr :+ renderKey(combo.key)).mkString("+")
  }

  private def renderKey(key: NamedKey): String = {
    key.name match {
      case s if s.length == 1 && s.head.isLetter => s"[Key${s.toUpperCase}]"
      case s if s.length == 1 && s.head.isDigit  => s
      case s                                     => s"[$s]"
    }
  }
}
