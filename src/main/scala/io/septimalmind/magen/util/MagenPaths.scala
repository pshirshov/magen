package io.septimalmind.magen.util

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystem, FileSystems, Files, Path, Paths}
import scala.jdk.CollectionConverters.*

sealed trait MappingsSource

object MappingsSource {
  case object Classpath extends MappingsSource
  case class Filesystem(path: Path) extends MappingsSource
}

sealed trait NegationsSource

object NegationsSource {
  case object Classpath extends NegationsSource
  case class Filesystem(path: Path) extends NegationsSource
}

object MagenPaths {
  private val MAPPINGS_PREFIX = "mappings"
  private val NEGATIONS_PREFIX = "negations"

  private var _source: MappingsSource = MappingsSource.Classpath
  private var _negationsSource: NegationsSource = NegationsSource.Classpath

  def configure(source: MappingsSource): Unit = {
    _source = source
  }

  def configureNegations(source: NegationsSource): Unit = {
    _negationsSource = source
  }

  def source: MappingsSource = _source
  def negationsSource: NegationsSource = _negationsSource

  def readMappingFile(relativePath: String): String = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        new String(Files.readAllBytes(base.resolve(relativePath)), StandardCharsets.UTF_8)
      case MappingsSource.Classpath =>
        readClasspathResource(s"$MAPPINGS_PREFIX/$relativePath")
    }
  }

  /** Reads a negation file. When filesystem source is configured, tries filesystem first,
    * falls back to classpath if the file doesn't exist on the filesystem. */
  def readNegationFile(relativePath: String): String = {
    _negationsSource match {
      case NegationsSource.Filesystem(base) =>
        val fsPath = base.resolve(relativePath)
        if (Files.exists(fsPath)) {
          new String(Files.readAllBytes(fsPath), StandardCharsets.UTF_8)
        } else {
          readClasspathResource(s"$NEGATIONS_PREFIX/$relativePath")
        }
      case NegationsSource.Classpath =>
        readClasspathResource(s"$NEGATIONS_PREFIX/$relativePath")
    }
  }

  def listSchemes(): List[String] = {
    listDirectories("")
  }

  def listSchemeFiles(schemeName: String): List[String] = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        val dir = base.resolve(schemeName)
        assert(Files.isDirectory(dir), s"Scheme directory not found: $dir")
        collectYamlFiles(dir)
          .map(f => dir.relativize(f).toString)
          .sorted
      case MappingsSource.Classpath =>
        withClasspathDir(MAPPINGS_PREFIX, schemeName) { dir =>
          collectYamlFiles(dir)
            .map(f => dir.relativize(f).toString)
            .sorted
        }.getOrElse(throw new AssertionError(s"Scheme not found on classpath: $schemeName"))
    }
  }

  def readSchemeFile(schemeName: String, fileName: String): String = {
    readMappingFile(s"$schemeName/$fileName")
  }

  /** For write operations (import) - requires Filesystem source (--mappings) */
  def writableDir: Path = {
    _source match {
      case MappingsSource.Filesystem(base) => base
      case MappingsSource.Classpath =>
        throw new AssertionError("--mappings DIR is required for write operations")
    }
  }

  private def readClasspathResource(fullPath: String): String = {
    val is = getClass.getClassLoader.getResourceAsStream(fullPath)
    assert(is != null, s"Resource not found in classpath: $fullPath")
    try new String(is.readAllBytes(), StandardCharsets.UTF_8)
    finally is.close()
  }

  private def listDirectories(relativePath: String): List[String] = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        val dir = if (relativePath.isEmpty) base else base.resolve(relativePath)
        if (Files.isDirectory(dir)) {
          Files.list(dir).iterator().asScala
            .filter(Files.isDirectory(_))
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case MappingsSource.Classpath =>
        val classpathPath = if (relativePath.isEmpty) MAPPINGS_PREFIX else s"$MAPPINGS_PREFIX/$relativePath"
        withClasspathDirRaw(classpathPath) { dir =>
          Files.list(dir).iterator().asScala
            .filter(Files.isDirectory(_))
            .map(_.getFileName.toString)
            .toList.sorted
        }.getOrElse(List.empty)
    }
  }

  private def withClasspathDir[T](prefix: String, relativePath: String)(f: Path => T): Option[T] = {
    val fullPath = if (relativePath.isEmpty) prefix else s"$prefix/$relativePath"
    withClasspathDirRaw(fullPath)(f)
  }

  private def withClasspathDirRaw[T](fullPath: String)(f: Path => T): Option[T] = {
    val url = getClass.getClassLoader.getResource(fullPath)
    if (url == null) return None

    url.getProtocol match {
      case "file" =>
        Some(f(Paths.get(url.toURI)))
      case "jar" =>
        val uri = url.toURI
        val parts = uri.toString.split("!")
        val jarUri = URI.create(parts(0))
        val jarFs = getOrCreateFileSystem(jarUri)
        val dir = jarFs.getPath(parts(1))
        Some(f(dir))
      case other =>
        throw new AssertionError(s"Unsupported classpath protocol: $other")
    }
  }

  private var _jarFs: FileSystem = _

  private def getOrCreateFileSystem(jarUri: URI): FileSystem = {
    if (_jarFs == null) {
      _jarFs = try FileSystems.getFileSystem(jarUri)
      catch { case _: java.nio.file.FileSystemNotFoundException =>
        FileSystems.newFileSystem(jarUri, java.util.Collections.emptyMap[String, Any]())
      }
    }
    _jarFs
  }

  private def collectYamlFiles(dir: Path): List[Path] = {
    Files.walk(dir).iterator().asScala
      .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".yaml"))
      .toList
  }
}
