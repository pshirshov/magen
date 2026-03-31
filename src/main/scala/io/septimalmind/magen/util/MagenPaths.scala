package io.septimalmind.magen.util

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

sealed trait MappingsSource

object MappingsSource {
  case object Bundled extends MappingsSource
  case class Filesystem(path: Path) extends MappingsSource
}

sealed trait NegationsSource

object NegationsSource {
  case object Bundled extends NegationsSource
  case class Filesystem(path: Path) extends NegationsSource
}

object MagenPaths {
  private val MAPPINGS_PREFIX  = "mappings"
  private val NEGATIONS_PREFIX = "negations"

  private var _source: MappingsSource           = MappingsSource.Bundled
  private var _negationsSource: NegationsSource = NegationsSource.Bundled

  def configure(source: MappingsSource): Unit = {
    _source = source
  }

  def configureNegations(source: NegationsSource): Unit = {
    _negationsSource = source
  }

  def source: MappingsSource           = _source
  def negationsSource: NegationsSource = _negationsSource

  def readMappingFile(relativePath: String): String = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        new String(Files.readAllBytes(base.resolve(relativePath)), StandardCharsets.UTF_8)
      case MappingsSource.Bundled =>
        BundledData.readFile(s"$MAPPINGS_PREFIX/$relativePath")
    }
  }

  /** Reads a negation file. When filesystem source is configured, tries filesystem first,
    * falls back to bundled data if the file doesn't exist on the filesystem.
    */
  def readNegationFile(relativePath: String): String = {
    _negationsSource match {
      case NegationsSource.Filesystem(base) =>
        val fsPath = base.resolve(relativePath)
        if (Files.exists(fsPath)) {
          new String(Files.readAllBytes(fsPath), StandardCharsets.UTF_8)
        } else {
          BundledData.readFile(s"$NEGATIONS_PREFIX/$relativePath")
        }
      case NegationsSource.Bundled =>
        BundledData.readFile(s"$NEGATIONS_PREFIX/$relativePath")
    }
  }

  def listSchemes(): List[String] = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        if (Files.isDirectory(base)) {
          Files
            .list(base).iterator().asScala
            .filter(Files.isDirectory(_))
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case MappingsSource.Bundled =>
        BundledData.listDirectories(MAPPINGS_PREFIX)
    }
  }

  def listSchemeFiles(schemeName: String): List[String] = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        val dir = base.resolve(schemeName)
        assert(Files.isDirectory(dir), s"Scheme directory not found: $dir")
        collectYamlFiles(dir)
          .map(f => dir.relativize(f).toString)
          .sorted
      case MappingsSource.Bundled =>
        val files = BundledData.walkFiles(s"$MAPPINGS_PREFIX/$schemeName", _.endsWith(".yaml"))
        assert(files.nonEmpty, s"Scheme not found on data path: $schemeName")
        files
    }
  }

  def readSchemeFile(schemeName: String, fileName: String): String = {
    readMappingFile(s"$schemeName/$fileName")
  }

  /** For write operations (import) - requires Filesystem source (--mappings) */
  def writableDir: Path = {
    _source match {
      case MappingsSource.Filesystem(base) => base
      case MappingsSource.Bundled =>
        throw new AssertionError("--mappings DIR is required for write operations")
    }
  }

  private def collectYamlFiles(dir: Path): List[Path] = {
    Files
      .walk(dir).iterator().asScala
      .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".yaml"))
      .toList
  }
}
