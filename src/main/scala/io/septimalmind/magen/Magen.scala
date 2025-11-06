package io.septimalmind.magen

import cats.syntax.either.*
import io.circe.generic.auto.*
import io.circe.{yaml, *}
import io.septimalmind.magen.model.*
import io.septimalmind.magen.targets.{IdeaInstaller, IdeaParams, VscodeInstaller, VscodeParams}
import io.septimalmind.magen.util.ShortcutParser
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.strings.IzString.*

import java.nio.file.{Path, Paths}
import scala.util.matching.Regex.quoteReplacement

// TODO: print duplicating key references
object Magen {
  def main(args: Array[String]): Unit = {
    val mapping = List(
      "mappings/ai.yaml",
      "mappings/generic-keys.yaml",
      "mappings/build.yaml",
      "mappings/clipboard.yaml",
      "mappings/commands.yaml",
      "mappings/cursor.yaml",
      "mappings/debug.yaml",
      "mappings/edit.yaml",
      "mappings/intellisense.yaml",
      "mappings/navigation.yaml",
      "mappings/search.yaml",
      "mappings/selection.yaml",
      "mappings/snippets.yaml",
      "mappings/transform.yaml",
      "mappings/ui.yaml",
      "mappings/vcs.yaml",
      "mappings/unset.yaml",
      "mappings/vscode-column.yaml",
      "mappings/vscode-files.yaml",
      "mappings/vscode-list.yaml",
      "mappings/vscode-other.yaml",
      "mappings/vscode-quickinput.yaml",
    )
      .map(f => Paths.get(f))
      .filter(_.toFile.exists())
      .map(f => readMapping(f))

    val converted = convert(mapping)

    val installers = List(
      new VscodeInstaller(
        VscodeParams(
          List(
//        "/home/pavel/work/safe/nix-gnome-lean/hosts/pavel-am5/vscode-keymap/linux/vscode-magen.json",
            "/home/pavel/.config/VSCodium/User/keybindings.json",
            "/home/pavel/work/safe/7mind/nix-config/users/pavel/hm/keymap-vscode-linux.json",
          ).map(p => Paths.get(p))
        )
      ),
      new IdeaInstaller(
        IdeaParams(
          List(
            "/home/pavel/.config/JetBrains/IntelliJIdea2024.3/keymaps/Magen.xml",
            "/home/pavel/.config/JetBrains/Rider2024.3/keymaps/Magen.xml",
            "/home/pavel/work/safe/7mind/nix-config/users/pavel/hm/keymap-idea-linux.xml",
          ).map(p => Paths.get(p)),
          negate = true,
          parent = "$default",
        )
      ),
    )

    installers.foreach(_.install(converted))
  }

  private def convert(mapping: List[RawMapping]): Mapping = {
    val allConcepts = mapping.flatMap(_.mapping.toSeq.flatten)
    val bad = allConcepts.map(m => (m.id, m)).toMultimap.filter(_._2.size > 1)
    if (bad.nonEmpty) {
      println(s"Conflicts: ${bad.niceList()}")
      ???
    }

    import izumi.fundamentals.collections.IzCollections.*
    val allKeys = mapping.flatMap(_.keys.map(_.toSeq).toSeq.flatten).toUniqueMap(identity)

    val vars = allKeys match {
      case Left(value) =>
        println(s"Conflicts: $value")
        ???
      case Right(value) =>
        value
    }

    val concepts = allConcepts.flatMap {
      c =>
        val i = c.idea.flatMap(i => i.action.map(a => IdeaAction(a, i.mouse.toList.flatten)))
        val v = c.vscode.flatMap(
          i =>
            i.action.map {
              a =>
                val bindings = i.binding.toList.flatten.map(expandTemplate(_, vars)).map(ShortcutParser.parseUnsafe)

                VSCodeAction(a, i.context.toList.flatten, bindings)
            }
        )
        val z = c.zed.flatMap(i => i.action.map(a => ZedAction(a, i.context.toList.flatten)))

        if (i.isEmpty && !c.idea.exists(_.missing.contains(true))) {
          println(s"${c.id}: not defined for IDEA")
        }
        if (v.isEmpty && !c.vscode.exists(_.missing.contains(true))) {
          println(s"${c.id}: not defined for VSCode")
        }
        if (z.isEmpty && !c.zed.exists(_.missing.contains(true)) && false) {
          println(s"${c.id}: not defined for Zed")
        }

        if (Seq(i, v, z).exists(_.nonEmpty) && c.binding.nonEmpty) {
          val chord = NEList
            .unsafeFrom(c.binding)
            .map(expandTemplate(_, vars))
            .map(ShortcutParser.parseUnsafe)
          Seq(Concept(c.id, chord, i, v, z))
        } else if (!c.unset.contains(true)) {
          println(s"Incomplete definition: ${c.id}")
          Seq.empty
        } else {
          Seq.empty
        }
    }

    Mapping(concepts.sortBy(_.id))
  }

  def expandTemplate(
    template: String,
    values: Map[String, String],
    maxIterations: Int = 10,
  ): String = {
    val pattern = """\$\{([^}]+)\}""".r

    var result = template
    var iteration = 0
    var previous = ""

    while (result != previous && iteration < maxIterations) {
      previous = result
      result = pattern.replaceAllIn(
        result,
        m => {
          val key = m.group(1)
          val replacement = values.getOrElse(key, m.matched)
          quoteReplacement(replacement)
        },
      )
      iteration += 1
    }
    result
  }

  private def readMapping(path: Path): RawMapping = {
    println(s"Reading $path")
    val input = IzFiles.readString(path)

    val json = yaml.v12.parser.parse(input)

    val mapping = json
      .leftMap(err => err: Error)
      .flatMap(_.as[RawMapping])
      .valueOr(throw _)
    mapping
  }
}
