package io.septimalmind.magen

import cats.syntax.either.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.yaml
import io.septimalmind.magen.model.Concept
import io.septimalmind.magen.targets.{IdeaRenderer, VSCodeRenderer}
import izumi.fundamentals.platform.files.IzFiles

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

case class Mapping(mapping: List[Concept])

object Magen {
  def main(args: Array[String]): Unit = {
    val mapping = List(
      "mappings/clipboard.yaml",
      "mappings/commands.yaml",
      "mappings/cursor.yaml",
      "mappings/edit.yaml",
      "mappings/intellisense.yaml",
      "mappings/navigation.yaml",
      "mappings/search.yaml",
      "mappings/selection.yaml",
      "mappings/transform.yaml",
      "mappings/ui.yaml",
      "mappings/vscode-column.yaml",
      "mappings/vscode-files.yaml",
      "mappings/vscode-list.yaml",
      "mappings/vscode-quickinput.yaml",
      "mappings/vscode-other.yaml",

//      "mappings/todo/vscode-idea-imported.yaml",
    )
      .map(f => Paths.get(f))
      .filter(_.toFile.exists())
      .map(f => readMapping(f))
      .flatMap(_.mapping)

    //    val renderers = List(VSCodeRenderer, ZedRenderer, IdeaRenderer)
    val renderers = List(IdeaRenderer, VSCodeRenderer)

    import izumi.fundamentals.collections.IzCollections.*
    import izumi.fundamentals.platform.strings.IzString.*
    val bad = mapping.map(m => (m.id, m)).toMultimap.filter(_._2.size > 1)
    if (bad.nonEmpty) {
      println(s"Conflicts: ${bad.niceList()}")
      ???
    }

    mapping.foreach {
      c =>
        if (c.idea.isEmpty || (c.idea.get.action.isEmpty && !c.idea.get.missing.contains(true))) {
          println(s"${c.id}: not defined for IDEA")
        }
    }

    renderers.foreach {
      r =>
        val rendered = r.render(Mapping(mapping.sortBy(_.id)))
        Files.write(Paths.get("target", r.id), rendered.getBytes(StandardCharsets.UTF_8))
    }
  }

  private def readMapping(path: Path): Mapping = {
    println(s"Reading $path")
    val input = IzFiles.readString(path)

    val json = yaml.v12.parser.parse(input)

    val mapping = json
      .leftMap(err => err: Error)
      .flatMap(_.as[Mapping])
      .valueOr(throw _)
    mapping
  }
}
