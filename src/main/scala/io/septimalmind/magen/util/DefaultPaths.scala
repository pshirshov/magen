package io.septimalmind.magen.util

import io.septimalmind.magen.model.Platform

import java.nio.file.{Path, Paths}

object DefaultPaths {
  private val home: String = System.getProperty("user.home")

  def vscodePaths(platform: Platform): List[String] = platform match {
    case Platform.MacOS =>
      List(
        "~/Library/Application Support/VSCodium/User/keybindings.json",
        "~/Library/Application Support/Code/User/keybindings.json",
      )
    case Platform.Linux =>
      List(
        "~/.config/VSCodium/User/keybindings.json",
        "~/.config/Code/User/keybindings.json",
      )
    case Platform.Win =>
      List(
        "~/AppData/Roaming/VSCodium/User/keybindings.json",
        "~/AppData/Roaming/Code/User/keybindings.json",
      )
  }

  def ideaPaths(platform: Platform, keymapName: String): List[String] = platform match {
    case Platform.MacOS =>
      List(
        s"~/Library/Application Support/JetBrains/*/keymaps/$keymapName.xml"
      )
    case Platform.Linux =>
      List(
        s"~/.config/JetBrains/*/keymaps/$keymapName.xml"
      )
    case Platform.Win =>
      List(
        s"~/AppData/Roaming/JetBrains/*/keymaps/$keymapName.xml"
      )
  }

  def zedPaths(platform: Platform): List[String] = platform match {
    case Platform.MacOS | Platform.Linux =>
      List(
        "~/.config/zed/keymap.json"
      )
    case Platform.Win =>
      List(
        "~/AppData/Roaming/Zed/keymap.json"
      )
  }

  def configPath(platform: Platform): Path = platform match {
    case Platform.MacOS =>
      Paths.get(home, "Library", "Application Support", "magen", "magen.json")
    case Platform.Linux =>
      Paths.get(home, ".config", "magen", "magen.json")
    case Platform.Win =>
      Paths.get(home, "AppData", "Roaming", "magen", "magen.json")
  }

  def ideaUserKeymapPatterns(platform: Platform): List[String] = platform match {
    case Platform.MacOS =>
      List(
        "~/Library/Application Support/JetBrains/*/keymaps/*.xml"
      )
    case Platform.Linux =>
      List(
        "~/.config/JetBrains/*/keymaps/*.xml"
      )
    case Platform.Win =>
      List(
        "~/AppData/Roaming/JetBrains/*/keymaps/*.xml"
      )
  }

  def ideaInstallationPatterns(platform: Platform): List[String] = platform match {
    case Platform.MacOS =>
      List(
        "~/Library/Application Support/JetBrains/Toolbox/apps/*/ch-0/*/",
        "/Applications/IntelliJ IDEA*.app/Contents/",
        "/Applications/Rider*.app/Contents/",
        "/Applications/PyCharm*.app/Contents/",
        "/Applications/WebStorm*.app/Contents/",
        "/Applications/GoLand*.app/Contents/",
        "/Applications/CLion*.app/Contents/",
        "/Applications/RustRover*.app/Contents/",
      )
    case Platform.Linux =>
      List(
        "~/.local/share/JetBrains/Toolbox/apps/*/ch-0/*/",
        "/opt/jetbrains/*/",
        "/snap/intellij-idea-*/current/",
      )
    case Platform.Win =>
      List(
        "~/AppData/Local/JetBrains/Toolbox/apps/*/ch-0/*/",
        "C:/Program Files/JetBrains/*/",
      )
  }
}
