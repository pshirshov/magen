package io.septimalmind.magen

import cats.syntax.either.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.yaml
import io.septimalmind.magen.model.*
import io.septimalmind.magen.targets.{IdeaRenderer, VSCodeRenderer}
import izumi.fundamentals.platform.files.IzFiles

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.strings.IzString.*

// TODO: key variables
// TODO: print duplicating key references
object Magen {
  def main(args: Array[String]): Unit = {
    val mapping = List(
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

    //    val renderers = List(VSCodeRenderer, ZedRenderer, IdeaRenderer)
    val renderers = List(IdeaRenderer, VSCodeRenderer)

    val converted = convert(mapping)
    renderers.foreach {
      r =>
        val rendered = r.render(converted) // Mapping(mapping.sortBy(_.id)))
        if (r.id == IdeaRenderer.id) {
          Files.write(Paths.get("/home/pavel/.config/JetBrains/IntelliJIdea2024.3/keymaps/Magen.xml"), rendered.getBytes(StandardCharsets.UTF_8))
        }
        if (r.id == VSCodeRenderer.id) {
          Files.write(Paths.get("/home/pavel/work/safe/nix-gnome-lean/hosts/pavel-am5/vscode-keymap/linux/vscode-magen.json"), rendered.getBytes(StandardCharsets.UTF_8))
        }
        Files.write(Paths.get("target", r.id), rendered.getBytes(StandardCharsets.UTF_8))
    }
  }

  private def convert(mapping: List[RawMapping]): Mapping = {
    val allConcepts = mapping.flatMap(_.mapping)
    val bad = allConcepts.map(m => (m.id, m)).toMultimap.filter(_._2.size > 1)
    if (bad.nonEmpty) {
      println(s"Conflicts: ${bad.niceList()}")
    }

    val concepts = allConcepts.flatMap {
      c =>
        val i = c.idea.flatMap(i => i.action.map(a => IdeaAction(a, i.mouse.toList.flatten)))
        val v = c.vscode.flatMap(i => i.action.map(a => VSCodeAction(a, i.context.toList.flatten, i.binding.toList.flatten)))
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
          Seq(Concept(c.id, NEList.unsafeFrom(c.binding), i, v, z))
        } else {
          println(s"Incomplete definition: ${c.id}")
          Seq.empty
        }
    }

    Mapping(concepts.sortBy(_.id))
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
