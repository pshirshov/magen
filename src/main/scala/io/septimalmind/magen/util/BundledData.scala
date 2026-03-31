package io.septimalmind.magen.util

import java.io.{BufferedInputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.xml.{Elem, XML}

sealed trait DataSource

object DataSource {
  case class Dir(path: Path) extends DataSource
  case object Classpath extends DataSource
}

/** Centralized access to bundled data files (mappings, negations, editor-mappings, idea-keymaps).
  *
  * In native-image mode, reads from a filesystem data directory.
  * In JVM mode, falls back to classpath resources.
  */
object BundledData {
  private var _source: DataSource = DataSource.Classpath

  def configure(source: DataSource): Unit = {
    _source = source
  }

  def source: DataSource = _source

  /** Auto-detect and configure the data source.
    * Resolution order:
    *   1. Already configured via configure() (e.g. from --data-dir CLI flag)
    *   2. MAGEN_DATA_DIR environment variable
    *   3. Relative to binary: ../share/magen/ (Nix install layout)
    *   4. Classpath fallback (JVM mode)
    */
  def autoDetect(env: Map[String, String] = sys.env): Unit = {
    _source match {
      case _: DataSource.Dir =>
        // Already explicitly configured, don't override
        return
      case DataSource.Classpath =>
      // Try to detect filesystem source
    }

    val detected = env
      .get("MAGEN_DATA_DIR")
      .map(Paths.get(_))
      .filter(Files.isDirectory(_))
      .orElse(detectRelativeToBinary())

    detected.foreach(dir => _source = DataSource.Dir(dir))
  }

  def readFile(relativePath: String): String = {
    _source match {
      case DataSource.Dir(base) =>
        val resolved = base.resolve(relativePath)
        assert(Files.exists(resolved), s"Data file not found: $resolved")
        new String(Files.readAllBytes(resolved), StandardCharsets.UTF_8)
      case DataSource.Classpath =>
        readClasspathResource(relativePath)
    }
  }

  def readFileOpt(relativePath: String): Option[String] = {
    _source match {
      case DataSource.Dir(base) =>
        val resolved = base.resolve(relativePath)
        if (Files.exists(resolved)) {
          Some(new String(Files.readAllBytes(resolved), StandardCharsets.UTF_8))
        } else {
          None
        }
      case DataSource.Classpath =>
        val is = getClass.getClassLoader.getResourceAsStream(relativePath)
        if (is == null) None
        else {
          try Some(new String(is.readAllBytes(), StandardCharsets.UTF_8))
          finally is.close()
        }
    }
  }

  def openInputStream(relativePath: String): Option[InputStream] = {
    _source match {
      case DataSource.Dir(base) =>
        val resolved = base.resolve(relativePath)
        if (Files.exists(resolved)) {
          Some(new BufferedInputStream(Files.newInputStream(resolved)))
        } else {
          None
        }
      case DataSource.Classpath =>
        Option(getClass.getClassLoader.getResourceAsStream(relativePath))
    }
  }

  def loadXml(relativePath: String): Option[Elem] = {
    openInputStream(relativePath).map {
      is =>
        try XML.load(new BufferedInputStream(is))
        finally is.close()
    }
  }

  def fileExists(relativePath: String): Boolean = {
    _source match {
      case DataSource.Dir(base) =>
        Files.exists(base.resolve(relativePath))
      case DataSource.Classpath =>
        getClass.getClassLoader.getResource(relativePath) != null
    }
  }

  def listFiles(dirPath: String): List[String] = {
    _source match {
      case DataSource.Dir(base) =>
        val dir = base.resolve(dirPath)
        if (Files.isDirectory(dir)) {
          Files
            .list(dir).iterator().asScala
            .filter(Files.isRegularFile(_))
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case DataSource.Classpath =>
        listClasspathDir(dirPath).filter {
          name =>
            val fullPath = s"$dirPath/$name"
            val url      = getClass.getClassLoader.getResource(fullPath)
            url != null && !isClasspathDirectory(fullPath)
        }
    }
  }

  def listDirectories(dirPath: String): List[String] = {
    _source match {
      case DataSource.Dir(base) =>
        val dir = base.resolve(dirPath)
        if (Files.isDirectory(dir)) {
          Files
            .list(dir).iterator().asScala
            .filter(Files.isDirectory(_))
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case DataSource.Classpath =>
        listClasspathDir(dirPath).filter {
          name =>
            isClasspathDirectory(s"$dirPath/$name")
        }
    }
  }

  /** Walk a directory recursively, returning relative paths of all regular files matching the filter. */
  def walkFiles(dirPath: String, filter: String => Boolean): List[String] = {
    _source match {
      case DataSource.Dir(base) =>
        val dir = base.resolve(dirPath)
        if (Files.isDirectory(dir)) {
          Files
            .walk(dir).iterator().asScala
            .filter(p => Files.isRegularFile(p) && filter(p.getFileName.toString))
            .map(p => dir.relativize(p).toString)
            .toList.sorted
        } else List.empty
      case DataSource.Classpath =>
        walkClasspathDir(dirPath, filter)
    }
  }

  private def detectRelativeToBinary(): Option[Path] = {
    // Linux: /proc/self/exe
    val procSelf = Paths.get("/proc/self/exe")
    if (Files.exists(procSelf)) {
      try {
        val binaryPath = procSelf.toRealPath()
        val shareDir   = binaryPath.getParent.getParent.resolve("share").resolve("magen")
        if (Files.isDirectory(shareDir)) return Some(shareDir)
      } catch {
        case _: Exception =>
      }
    }
    None
  }

  private def readClasspathResource(fullPath: String): String = {
    val is = getClass.getClassLoader.getResourceAsStream(fullPath)
    assert(is != null, s"Resource not found in classpath: $fullPath")
    try new String(is.readAllBytes(), StandardCharsets.UTF_8)
    finally is.close()
  }

  private def isClasspathDirectory(path: String): Boolean = {
    // Heuristic: directories on classpath can be listed
    // Try to get the resource as a URL and check protocol
    val url = getClass.getClassLoader.getResource(path)
    if (url == null) return false
    url.getProtocol match {
      case "file" =>
        Paths.get(url.toURI).toFile.isDirectory
      case "jar" =>
        // In JARs, directories end with /
        val withSlash = if (path.endsWith("/")) path else s"$path/"
        getClass.getClassLoader.getResource(withSlash) != null
      case _ => false
    }
  }

  private def listClasspathDir(dirPath: String): List[String] = {
    import java.net.URI
    val url = getClass.getClassLoader.getResource(dirPath)
    if (url == null) return List.empty

    url.getProtocol match {
      case "file" =>
        val dir = Paths.get(url.toURI)
        if (Files.isDirectory(dir)) {
          Files
            .list(dir).iterator().asScala
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case "jar" =>
        val uri    = url.toURI
        val parts  = uri.toString.split("!")
        val jarUri = URI.create(parts(0))
        val jarFs  = getOrCreateJarFs(jarUri)
        val dir    = jarFs.getPath(parts(1))
        if (Files.isDirectory(dir)) {
          Files
            .list(dir).iterator().asScala
            .map(_.getFileName.toString)
            .toList.sorted
        } else List.empty
      case other =>
        throw new AssertionError(s"Unsupported classpath protocol: $other")
    }
  }

  private def walkClasspathDir(dirPath: String, filter: String => Boolean): List[String] = {
    import java.net.URI
    val url = getClass.getClassLoader.getResource(dirPath)
    if (url == null) return List.empty

    url.getProtocol match {
      case "file" =>
        val dir = Paths.get(url.toURI)
        if (Files.isDirectory(dir)) {
          Files
            .walk(dir).iterator().asScala
            .filter(p => Files.isRegularFile(p) && filter(p.getFileName.toString))
            .map(p => dir.relativize(p).toString)
            .toList.sorted
        } else List.empty
      case "jar" =>
        val uri    = url.toURI
        val parts  = uri.toString.split("!")
        val jarUri = URI.create(parts(0))
        val jarFs  = getOrCreateJarFs(jarUri)
        val dir    = jarFs.getPath(parts(1))
        if (Files.isDirectory(dir)) {
          Files
            .walk(dir).iterator().asScala
            .filter(p => Files.isRegularFile(p) && filter(p.getFileName.toString))
            .map(p => dir.relativize(p).toString)
            .toList.sorted
        } else List.empty
      case other =>
        throw new AssertionError(s"Unsupported classpath protocol: $other")
    }
  }

  private var _jarFs: java.nio.file.FileSystem = _

  private def getOrCreateJarFs(jarUri: java.net.URI): java.nio.file.FileSystem = {
    if (_jarFs == null) {
      import java.nio.file.FileSystems
      _jarFs =
        try FileSystems.getFileSystem(jarUri)
        catch {
          case _: java.nio.file.FileSystemNotFoundException =>
            FileSystems.newFileSystem(jarUri, java.util.Collections.emptyMap[String, Any]())
        }
    }
    _jarFs
  }
}
