package io.septimalmind.magen.util

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object PathExpander {

  def expandGlob(pattern: String): List[Path] = {
    val normalized = expandTilde(pattern)
    val hasWildcard = containsWildcard(normalized)
    val segments = normalized.split("/").toList.filter(_.nonEmpty)
    val root = if (normalized.startsWith("/")) Paths.get("/") else Paths.get(".")
    expandPath(segments, root, allowCreate = !hasWildcard)
  }

  private def expandTilde(path: String): String = {
    if (path.startsWith("~/")) {
      val home = System.getProperty("user.home")
      home + path.substring(1)
    } else if (path == "~") {
      System.getProperty("user.home")
    } else {
      path
    }
  }

  private def expandPath(segments: List[String], currentPath: Path, allowCreate: Boolean): List[Path] = {
    segments match {
      case Nil =>
        if (Files.exists(currentPath)) List(currentPath) else Nil

      case segment :: remaining =>
        if (containsWildcard(segment)) {
          expandWildcard(currentPath, segment).flatMap { expanded =>
            expandPath(remaining, expanded, allowCreate = false)
          }
        } else {
          val nextPath = currentPath.resolve(segment)
          if (remaining.isEmpty) {
            if (Files.exists(nextPath)) {
              List(nextPath)
            } else if (allowCreate && Files.exists(currentPath) && Files.isDirectory(currentPath)) {
              List(nextPath)
            } else {
              Nil
            }
          } else {
            if (Files.exists(nextPath) && Files.isDirectory(nextPath)) {
              expandPath(remaining, nextPath, allowCreate)
            } else {
              Nil
            }
          }
        }
    }
  }

  private def containsWildcard(segment: String): Boolean = {
    segment.contains("*") || segment.contains("?")
  }

  private def expandWildcard(directory: Path, pattern: String): List[Path] = {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      return Nil
    }

    val matcher = directory.getFileSystem.getPathMatcher(s"glob:$pattern")

    try {
      Files.list(directory)
        .iterator()
        .asScala
        .filter { path =>
          matcher.matches(path.getFileName)
        }
        .toList
    } catch {
      case _: Exception => Nil
    }
  }

  def expandGlobs(patterns: List[String]): List[Path] = {
    patterns.flatMap(expandGlob).distinct
  }
}
