package io.septimalmind.magen.importer

import io.circe.syntax.*
import io.circe.Printer

import java.io.BufferedInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.util.Using
import scala.xml.XML

object IdeaNegationGenerator {
  def extractActionsFromXml(xmlPath: Path): Set[String] = {
    val xml = XML.loadFile(xmlPath.toFile)
    (xml \\ "action").map(a => (a \ "@id").text).filter(_.nonEmpty).toSet
  }

  def extractFromBundledDefault(): Set[String] = {
    val keymaps = IdeaSchemeImporter.listKeymaps()
    val bundledDefaults = keymaps.filter(km => km.bundled && km.name == "$default")

    bundledDefaults.flatMap { km =>
      extractActionsFromJar(km.source, "$default")
    }.toSet
  }

  def writeActionsJson(actions: Set[String], outputPath: Path): Unit = {
    val sorted = actions.toList.sorted
    val json = sorted.asJson.printWith(Printer.spaces2)
    val _ = Files.write(outputPath, json.getBytes(StandardCharsets.UTF_8))
  }

  private def extractActionsFromJar(jarPath: Path, keymapName: String): Set[String] = {
    try {
      Using(new ZipFile(jarPath.toFile)) { zip =>
        val entries = scala.jdk.CollectionConverters.EnumerationHasAsScala(zip.entries()).asScala
        entries
          .filter(e => e.getName.startsWith("keymaps/") && e.getName.endsWith(".xml"))
          .flatMap { entry =>
            try {
              Using(new BufferedInputStream(zip.getInputStream(entry))) { is =>
                val xml = XML.load(is)
                val name = (xml \ "@name").text
                if (name == keymapName) {
                  (xml \\ "action").map(a => (a \ "@id").text).filter(_.nonEmpty).toSet
                } else {
                  Set.empty[String]
                }
              }.getOrElse(Set.empty[String])
            } catch {
              case _: Exception => Set.empty[String]
            }
          }
          .toSet
      }.getOrElse(Set.empty)
    } catch {
      case _: Exception => Set.empty
    }
  }
}
