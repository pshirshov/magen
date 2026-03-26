package io.septimalmind.magen.model

sealed trait Platform

object Platform {
  case object MacOS extends Platform
  case object Linux extends Platform
  case object Win extends Platform

  def detect(): Platform = {
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("mac")) MacOS
    else if (os.contains("win")) Win
    else Linux
  }

  def parse(value: String): Platform = {
    value.toLowerCase match {
      case "macos" | "mac"     => MacOS
      case "linux"             => Linux
      case "win" | "windows"   => Win
      case other =>
        throw new IllegalArgumentException(
          s"Unknown platform: $other. Expected: macos, linux, win"
        )
    }
  }
}
