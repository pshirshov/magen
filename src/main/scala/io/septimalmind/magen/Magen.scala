package io.septimalmind.magen

import cats.syntax.either._
import io.circe._
import io.circe.generic.auto._
import io.circe.yaml
import izumi.fundamentals.platform.files.IzFiles

import java.nio.file.Paths

case class IdeaAction(action: String)
case class VSCodeAction(action: String, context: Option[List[String]])
case class ZedAction(action: String, context: Option[List[String]])

case class Impl(
  target: String,
  action: String,
  context: Option[List[String]],
)

case class Concept(
  id: String,
  binding: String,
  idea: Option[IdeaAction],
  vscode: Option[VSCodeAction],
  zed: Option[ZedAction],
)

case class Mapping(mapping: List[Concept])

object Magen {
  def main(args: Array[String]): Unit = {
    val input = IzFiles.readString(Paths.get("mapping.yaml"))

    val json = yaml.v12.parser.parse(input)

    val mapping = json
      .leftMap(err => err: Error)
      .flatMap(_.as[Mapping])
      .valueOr(throw _)

//    val renderers = List(VSCodeRenderer, ZedRenderer, IdeaRenderer)
    val renderers = List(IdeaRenderer, VSCodeRenderer)

    renderers.foreach {
      r =>
        val rendered = r.render(mapping)
        println(rendered)
    }
  }
}
