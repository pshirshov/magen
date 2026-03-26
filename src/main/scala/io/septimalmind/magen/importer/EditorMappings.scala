package io.septimalmind.magen.importer

import io.circe.*
import io.circe.parser

import java.nio.charset.StandardCharsets

case class EditorActionRef(
  action: String,
  context: List[String],
)

case class EditorEquivalents(
  idea: Option[EditorActionRef],
  vscode: Option[EditorActionRef],
  zed: Option[EditorActionRef],
)

object EditorMappings {
  private val RESOURCE_PREFIX = "editor-mappings"
  private val EMPTY = EditorEquivalents(None, None, None)

  private lazy val fromIdea: MappingIndex = loadMappingFile("from-idea.json")
  private lazy val fromVscode: MappingIndex = loadMappingFile("from-vscode.json")
  private lazy val fromZed: MappingIndex = loadMappingFile("from-zed.json")

  def lookupFromIdea(ideaAction: String): EditorEquivalents = {
    fromIdea.lookup(ideaAction, List.empty)
  }

  def lookupFromVscode(vscodeAction: String, context: List[String]): EditorEquivalents = {
    fromVscode.lookup(vscodeAction, context)
  }

  def lookupFromZed(zedAction: String, context: List[String]): EditorEquivalents = {
    fromZed.lookup(zedAction, context)
  }

  /** Index that supports composite key lookup with action-only fallback.
    *
    * Keys in mapping files can be:
    * - Plain action: "\\$Copy" (from-idea.json)
    * - Composite: "editor::Copy|Editor" (from-vscode.json, from-zed.json)
    *
    * Lookup order:
    * 1. Exact composite key: "action|context1,context2"
    * 2. Exact action key (no pipe): "action"
    * 3. Action-only fallback: first entry whose key starts with "action|"
    */
  private case class MappingIndex(
    byKey: Map[String, EditorEquivalents],
    byAction: Map[String, EditorEquivalents],
  ) {
    def lookup(action: String, context: List[String]): EditorEquivalents = {
      if (context.nonEmpty) {
        val compositeKey = s"$action|${context.mkString(",")}"
        byKey.get(compositeKey)
          .orElse(byKey.get(action))
          .orElse(byAction.get(action))
          .getOrElse(EMPTY)
      } else {
        byKey.get(action)
          .orElse(byAction.get(action))
          .getOrElse(EMPTY)
      }
    }
  }

  private def loadMappingFile(fileName: String): MappingIndex = {
    val is = getClass.getClassLoader.getResourceAsStream(s"$RESOURCE_PREFIX/$fileName")
    if (is == null) return MappingIndex(Map.empty, Map.empty)
    val content = try new String(is.readAllBytes(), StandardCharsets.UTF_8) finally is.close()

    parser.parse(content) match {
      case Left(_) => MappingIndex(Map.empty, Map.empty)
      case Right(json) => buildIndex(json)
    }
  }

  private def buildIndex(json: Json): MappingIndex = {
    json.asObject match {
      case None => MappingIndex(Map.empty, Map.empty)
      case Some(obj) =>
        val byKey = obj.toMap.map { case (key, value) =>
          key -> parseEquivalents(value)
        }
        // Build action-only fallback: for composite keys "action|context",
        // keep the first entry per action name
        val byAction = byKey.toList.collect {
          case (key, equiv) if key.contains('|') =>
            key.substring(0, key.indexOf('|')) -> equiv
        }.groupBy(_._1).map { case (action, entries) =>
          action -> entries.head._2
        }
        MappingIndex(byKey, byAction)
    }
  }

  private def parseEquivalents(json: Json): EditorEquivalents = {
    val obj = json.asObject.getOrElse(JsonObject.empty)
    EditorEquivalents(
      idea = obj("idea").flatMap(parseActionRef),
      vscode = obj("vscode").flatMap(parseActionRef),
      zed = obj("zed").flatMap(parseActionRef),
    )
  }

  private def parseActionRef(json: Json): Option[EditorActionRef] = {
    json.asObject.flatMap { obj =>
      obj("action").flatMap(_.asString).map { action =>
        val context = obj("context")
          .flatMap(_.asArray)
          .map(_.flatMap(_.asString).toList)
          .getOrElse(List.empty)
        EditorActionRef(action, context)
      }
    }
  }
}
