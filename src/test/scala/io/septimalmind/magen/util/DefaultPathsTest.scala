package io.septimalmind.magen.util

import io.septimalmind.magen.model.Platform
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class DefaultPathsTest extends AnyWordSpec with Matchers {

  "DefaultPaths.vscodePaths" should {
    "return macOS Application Support paths on macOS" in {
      val paths = DefaultPaths.vscodePaths(Platform.MacOS)
      paths should have size 2
      all(paths) should include("Library/Application Support")
      paths.exists(_.contains("VSCodium")) shouldBe true
      paths.exists(_.contains("Code")) shouldBe true
    }

    "return XDG config paths on Linux" in {
      val paths = DefaultPaths.vscodePaths(Platform.Linux)
      paths should have size 2
      all(paths) should include(".config")
      paths.exists(_.contains("VSCodium")) shouldBe true
      paths.exists(_.contains("Code")) shouldBe true
    }

    "return AppData paths on Windows" in {
      val paths = DefaultPaths.vscodePaths(Platform.Win)
      paths should have size 2
      all(paths) should include("AppData/Roaming")
      paths.exists(_.contains("VSCodium")) shouldBe true
      paths.exists(_.contains("Code")) shouldBe true
    }
  }

  "DefaultPaths.ideaPaths" should {
    "include keymap name in all platform paths" in {
      val keymapName = "Magen-test"
      for (platform <- List(Platform.MacOS, Platform.Linux, Platform.Win)) {
        val paths = DefaultPaths.ideaPaths(platform, keymapName)
        paths should not be empty
        all(paths) should include(s"$keymapName.xml")
        all(paths) should include("JetBrains")
      }
    }

    "use Application Support on macOS" in {
      val paths = DefaultPaths.ideaPaths(Platform.MacOS, "Test")
      all(paths) should include("Library/Application Support")
    }

    "use .config on Linux" in {
      val paths = DefaultPaths.ideaPaths(Platform.Linux, "Test")
      all(paths) should include(".config")
    }

    "use AppData on Windows" in {
      val paths = DefaultPaths.ideaPaths(Platform.Win, "Test")
      all(paths) should include("AppData/Roaming")
    }
  }

  "DefaultPaths.zedPaths" should {
    "use .config/zed on macOS and Linux" in {
      for (platform <- List(Platform.MacOS, Platform.Linux)) {
        val paths = DefaultPaths.zedPaths(platform)
        paths should have size 1
        paths.head should include(".config/zed/keymap.json")
      }
    }

    "use AppData on Windows" in {
      val paths = DefaultPaths.zedPaths(Platform.Win)
      paths should have size 1
      paths.head should include("AppData/Roaming/Zed/keymap.json")
    }
  }

  "DefaultPaths.configPath" should {
    "use Application Support on macOS" in {
      val path = DefaultPaths.configPath(Platform.MacOS)
      path.toString should include("Library")
      path.toString should include("Application Support")
      path.toString should endWith("magen.json")
    }

    "use .config on Linux" in {
      val path = DefaultPaths.configPath(Platform.Linux)
      path.toString should include(".config")
      path.toString should endWith("magen.json")
    }

    "use AppData on Windows" in {
      val path = DefaultPaths.configPath(Platform.Win)
      path.toString should include("AppData")
      path.toString should endWith("magen.json")
    }

    "return distinct paths for each platform" in {
      val macPath = DefaultPaths.configPath(Platform.MacOS)
      val linuxPath = DefaultPaths.configPath(Platform.Linux)
      val winPath = DefaultPaths.configPath(Platform.Win)
      macPath should not equal linuxPath
      macPath should not equal winPath
      linuxPath should not equal winPath
    }
  }

  "DefaultPaths.ideaUserKeymapPatterns" should {
    "use Application Support on macOS" in {
      val patterns = DefaultPaths.ideaUserKeymapPatterns(Platform.MacOS)
      all(patterns) should include("Library/Application Support")
      all(patterns) should include("keymaps/*.xml")
    }

    "use .config on Linux" in {
      val patterns = DefaultPaths.ideaUserKeymapPatterns(Platform.Linux)
      all(patterns) should include(".config")
      all(patterns) should include("keymaps/*.xml")
    }

    "use AppData on Windows" in {
      val patterns = DefaultPaths.ideaUserKeymapPatterns(Platform.Win)
      all(patterns) should include("AppData/Roaming")
      all(patterns) should include("keymaps/*.xml")
    }
  }

  "DefaultPaths.ideaInstallationPatterns" should {
    "include macOS-specific patterns on macOS" in {
      val patterns = DefaultPaths.ideaInstallationPatterns(Platform.MacOS)
      patterns should not be empty
      patterns.exists(_.contains("/Applications/")) shouldBe true
      patterns.exists(_.endsWith(".app/Contents/")) shouldBe true
    }

    "include Linux-specific patterns on Linux" in {
      val patterns = DefaultPaths.ideaInstallationPatterns(Platform.Linux)
      patterns should not be empty
      patterns.exists(_.contains("/opt/")) shouldBe true
    }

    "include Windows-specific patterns on Windows" in {
      val patterns = DefaultPaths.ideaInstallationPatterns(Platform.Win)
      patterns should not be empty
      patterns.exists(_.contains("Program Files")) shouldBe true
    }
  }

  "All DefaultPaths methods" should {
    "cover all platforms without throwing" in {
      for (platform <- List(Platform.MacOS, Platform.Linux, Platform.Win)) {
        DefaultPaths.vscodePaths(platform) should not be empty
        DefaultPaths.ideaPaths(platform, "Test") should not be empty
        DefaultPaths.zedPaths(platform) should not be empty
        DefaultPaths.configPath(platform).toString should not be empty
        DefaultPaths.ideaUserKeymapPatterns(platform) should not be empty
        DefaultPaths.ideaInstallationPatterns(platform) should not be empty
      }
    }
  }
}
