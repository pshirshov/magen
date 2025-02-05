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
      "mappings/mapping.yaml",
      "mappings/vscode-idea-imported.yaml",
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
    renderers.foreach {
      r =>
        val rendered = r.render(Mapping(mapping))
        Files.write(Paths.get("target", r.id), rendered.getBytes(StandardCharsets.UTF_8))
    }
  }

  private def readMapping(path: Path) = {
    val input = IzFiles.readString(path)

    val json = yaml.v12.parser.parse(input)

    val mapping = json
      .leftMap(err => err: Error)
      .flatMap(_.as[Mapping])
      .valueOr(throw _)
    mapping
  }
}
