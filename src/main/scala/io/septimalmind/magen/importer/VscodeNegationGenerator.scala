package io.septimalmind.magen.importer

import io.circe.*
import io.circe.syntax.*
import izumi.fundamentals.platform.files.IzFiles

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object VscodeNegationGenerator {
  def generateFromDefaults(defaultsPath: Path, outputPath: Path): Unit = {
    val content = IzFiles.readString(defaultsPath)
    val entries = parser
      .parse(content)
      .flatMap(_.as[List[JsonObject]])
      .getOrElse(throw new RuntimeException(s"Failed to parse VSCode defaults from $defaultsPath"))

    val negations = entries.flatMap {
      obj =>
        val command = obj("command").flatMap(_.asString)
        val key     = obj("key").flatMap(_.asString)

        (command, key) match {
          case (Some(cmd), Some(k)) if !cmd.startsWith("-") =>
            Some(
              JsonObject(
                "key"     -> Json.fromString(k),
                "command" -> Json.fromString(s"-$cmd"),
              )
            )
          case _ => None
        }
    }

    val json = negations.asJson.printWith(Printer.spaces2)
    Files.write(outputPath, json.getBytes(StandardCharsets.UTF_8))
    println(s"Generated ${negations.size} negation entries")
  }
}
