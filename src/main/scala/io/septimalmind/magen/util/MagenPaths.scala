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

object MagenPaths {
  private val RESOURCE_PREFIX = "mappings"

  private var _source: MappingsSource = MappingsSource.Classpath

  def configure(source: MappingsSource): Unit = {
    _source = source
  }

  def source: MappingsSource = _source

  def readMappingFile(relativePath: String): String = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        new String(Files.readAllBytes(base.resolve(relativePath)), StandardCharsets.UTF_8)
      case MappingsSource.Classpath =>
        val is = getClass.getClassLoader.getResourceAsStream(s"$RESOURCE_PREFIX/$relativePath")
        assert(is != null, s"Resource not found in classpath: $RESOURCE_PREFIX/$relativePath")
        try new String(is.readAllBytes(), StandardCharsets.UTF_8)
        finally is.close()
    }
  }

  def listSchemes(): List[String] = {
    listDirectories("schemes")
  }

  def listSchemeFiles(schemeName: String): List[String] = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        val dir = base.resolve("schemes").resolve(schemeName)
        assert(Files.isDirectory(dir), s"Scheme directory not found: $dir")
        collectYamlFiles(dir)
          .map(f => dir.relativize(f).toString)
          .sorted
      case MappingsSource.Classpath =>
        withClasspathDir(s"schemes/$schemeName") { dir =>
          collectYamlFiles(dir)
            .map(f => dir.relativize(f).toString)
            .sorted
        }.getOrElse(throw new AssertionError(s"Scheme not found on classpath: $schemeName"))
    }
  }

  def readSchemeFile(schemeName: String, fileName: String): String = {
    readMappingFile(s"schemes/$schemeName/$fileName")
  }

  def readSharedFile(relativePath: String): String = {
    readMappingFile(s"shared/$relativePath")
  }

  /** For write operations (import, negate) - always filesystem */
  def writableDir: Path = {
    _source match {
      case MappingsSource.Filesystem(base) => base
      case MappingsSource.Classpath => Paths.get("mappings")
    }
  }

  private def listDirectories(relativePath: String): List[String] = {
    _source match {
      case MappingsSource.Filesystem(base) =>
        val dir = base.resolve(relativePath)
        if (Files.isDirectory(dir)) {
          Files.list(dir).iterator().asScala
            .filter(Files.isDirectory(_))
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case MappingsSource.Classpath =>
        withClasspathDir(relativePath) { dir =>
          Files.list(dir).iterator().asScala
            .filter(Files.isDirectory(_))
            .map(_.getFileName.toString)
            .toList.sorted
        }.getOrElse(List.empty)
    }
  }

  private def withClasspathDir[T](relativePath: String)(f: Path => T): Option[T] = {
    val url = getClass.getClassLoader.getResource(s"$RESOURCE_PREFIX/$relativePath")
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
