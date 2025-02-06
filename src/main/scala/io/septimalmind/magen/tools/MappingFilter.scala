package io.septimalmind.magen.tools

import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.septimalmind.magen.targets.IdeaRenderer
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.resources.IzResources

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}



object MappingFilter {

  def sortKeys(json: JsonObject): JsonObject =
    json.toList.sortBy(_._1).foldLeft(JsonObject.empty)((o, kv) => o.add(kv._1, kv._2))
  val basicMappings: Map[String, String] = {
    val fv = IzResources.readAsString("idea-vscode-mapping.json").get
    val pv = parser.parse(fv)
    val jv = pv.flatMap(_.as[Map[String, String]]).toOption.get.map(_.swap).toMap
    jv
  }
  def main(args: Array[String]): Unit = {


    val fv = IzFiles.readString(Paths.get("./junk/vscode-default.json"))
    val pv = parser.parse(fv)
    val jv = pv.flatMap(_.as[List[VscodeMapping]]).toOption.get.map(_.command).toSet

    val ja = IdeaRenderer.allIdeaActions()

    val f = IzFiles.readString(Paths.get("./junk/idea-vscode-mapping-draft.json"))
    val p = parser.parse(f)
    val j = p
      .flatMap(_.as[Map[String, Option[String]]]).toOption.get.filterNot(_._2.isEmpty).view.mapValues(_.get)
      .filter(f => ja.contains(f._1) && jv.contains(f._2)).toSeq

    val bm = basicMappings.map(_.swap).toSeq

    import izumi.fundamentals.collections.IzCollections.*
    val all = j ++ bm
    val bad = all.toMultimap.filter(_._2.size > 1)
    assert(bad.isEmpty)

    val safeMapping = sortKeys(all.toMap.asJsonObject).asJson.spaces2

    Files.write(Paths.get("./junk/idea-vscode-mapping-filtered.json"), safeMapping.getBytes(StandardCharsets.UTF_8))
    ()
  }

}
