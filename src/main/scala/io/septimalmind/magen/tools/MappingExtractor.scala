package io.septimalmind.magen.tools

import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.yaml
import io.septimalmind.magen.model.{RawConcept, RawMapping}
import io.septimalmind.magen.util.{MagenPaths, MappingsSource}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

object MappingExtractor {

  case class EditorAction(action: String, context: Option[List[String]])

  case class MappingEntry(
    idea: Option[EditorAction],
    vscode: Option[EditorAction],
    zed: Option[EditorAction],
  )

  def main(args: Array[String]): Unit = {
    MagenPaths.configure(MappingsSource.Bundled)
    val schemes = List("pshirshov", "idea-macos")

    val allConcepts = schemes.flatMap {
      scheme =>
        val files = MagenPaths.listSchemeFiles(scheme)
        files.flatMap {
          file =>
            val content = MagenPaths.readSchemeFile(scheme, file)
            val parsed  = yaml.v12.parser.parse(content).flatMap(_.as[RawMapping]).toOption
            parsed.toList.flatMap(_.mapping.toList.flatten)
        }
    }

    val entries = allConcepts.flatMap(extractMapping)

    val unique = entries
      .groupBy(e => (e.idea.map(_.action), e.vscode.map(_.action), e.zed.map(_.action)))
      .values.map(_.head).toList
      .sortBy(e => (e.idea.map(_.action).getOrElse(""), e.vscode.map(_.action).getOrElse("")))

    val fromIdea   = buildFromIdea(unique)
    val fromVscode = buildFromVscode(unique)
    val fromZed    = buildFromZed(unique)

    val outDir = Paths.get("src/main/resources/editor-mappings")
    Files.createDirectories(outDir)

    writeJson(outDir.resolve("from-idea.json"), fromIdea)
    writeJson(outDir.resolve("from-vscode.json"), fromVscode)
    writeJson(outDir.resolve("from-zed.json"), fromZed)

    // Also update the legacy idea-vscode-mapping.json
    val legacyMap = unique
      .filter(e => e.idea.isDefined && e.vscode.isDefined)
      .map(e => (e.idea.get.action, e.vscode.get.action))
      .sortBy(_._1)
    val legacyJson = JsonObject.fromIterable(legacyMap.map { case (k, v) => (k, Json.fromString(v)) })
    writeJson(Paths.get("src/main/resources/idea-vscode-mapping.json"), legacyJson.asJson)

    println(s"Total unique cross-editor mappings: ${unique.size}")
    println(s"  idea+vscode+zed: ${unique.count(e => e.idea.isDefined && e.vscode.isDefined && e.zed.isDefined)}")
    println(s"  idea+vscode only: ${unique.count(e => e.idea.isDefined && e.vscode.isDefined && e.zed.isEmpty)}")
    println(s"  idea+zed only: ${unique.count(e => e.idea.isDefined && e.vscode.isEmpty && e.zed.isDefined)}")
    println(s"  vscode+zed only: ${unique.count(e => e.idea.isEmpty && e.vscode.isDefined && e.zed.isDefined)}")
    println("Done.")
  }

  private def extractMapping(c: RawConcept): Option[MappingEntry] = {
    val idea = c.idea.flatMap(i => i.action.filter(_ => !i.missing.contains(true)).map(a => EditorAction(a, None)))
    val vscode = c.vscode.flatMap(
      v =>
        v.action.filter(_ => !v.missing.contains(true)).map {
          a =>
            val ctx = v.context.filter(_.nonEmpty)
            EditorAction(a, ctx)
        }
    )
    val zed = c.zed.flatMap(
      z =>
        z.action.filter(_ => !z.missing.contains(true)).map {
          a =>
            val ctx = z.context.filter(_.nonEmpty)
            EditorAction(a, ctx)
        }
    )

    val defined = Seq(idea, vscode, zed).count(_.isDefined)
    if (defined >= 2) Some(MappingEntry(idea, vscode, zed)) else None
  }

  private def editorActionJson(ea: EditorAction): Json = {
    val fields = List("action" -> Json.fromString(ea.action)) ++
      ea.context.map(ctx => "context" -> Json.fromValues(ctx.map(Json.fromString)))
    Json.fromJsonObject(JsonObject.fromIterable(fields))
  }

  private def buildFromIdea(entries: List[MappingEntry]): Json = {
    val pairs = entries.filter(_.idea.isDefined).map {
      e =>
        val targets = List(
          e.vscode.map(v => "vscode" -> editorActionJson(v)),
          e.zed.map(z => "zed" -> editorActionJson(z)),
        ).flatten
        e.idea.get.action -> Json.fromJsonObject(JsonObject.fromIterable(targets))
    }
    Json.fromJsonObject(JsonObject.fromIterable(pairs))
  }

  private def buildFromVscode(entries: List[MappingEntry]): Json = {
    val pairs = entries.filter(_.vscode.isDefined).map {
      e =>
        val key = e.vscode.get
        val keyStr = if (key.context.exists(_.nonEmpty)) {
          s"${key.action}|${key.context.get.mkString(",")}"
        } else key.action
        val targets = List(
          e.idea.map(i => "idea" -> editorActionJson(i)),
          e.zed.map(z => "zed" -> editorActionJson(z)),
        ).flatten
        keyStr -> Json.fromJsonObject(JsonObject.fromIterable(targets))
    }
    Json.fromJsonObject(JsonObject.fromIterable(pairs))
  }

  private def buildFromZed(entries: List[MappingEntry]): Json = {
    val pairs = entries.filter(_.zed.isDefined).map {
      e =>
        val key = e.zed.get
        val keyStr = if (key.context.exists(_.nonEmpty)) {
          s"${key.action}|${key.context.get.mkString(",")}"
        } else key.action
        val targets = List(
          e.idea.map(i => "idea" -> editorActionJson(i)),
          e.vscode.map(v => "vscode" -> editorActionJson(v)),
        ).flatten
        keyStr -> Json.fromJsonObject(JsonObject.fromIterable(targets))
    }
    Json.fromJsonObject(JsonObject.fromIterable(pairs))
  }

  private def writeJson(path: Path, json: Json): Unit = {
    val content = json.printWith(Printer.spaces2)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    println(s"Wrote $path")
  }
}
