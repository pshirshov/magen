package io.septimalmind.magen.util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class MagenPathsTest extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    MagenPaths.configure(MappingsSource.Classpath)
    MagenPaths.configureNegations(NegationsSource.Classpath)
  }

  "MagenPaths in Classpath mode" should {
    "list bundled schemes" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val schemes = MagenPaths.listSchemes()
      schemes should contain("pshirshov")
      schemes should contain("idea-macos")
      schemes should contain("from-idea")
    }

    "list scheme files for a bundled scheme" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val files = MagenPaths.listSchemeFiles("pshirshov")
      files should not be empty
      files should contain("search.yaml")
      files should contain("edit.yaml")
      all(files) should endWith(".yaml")
    }

    "list scheme files including subdirectories" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val files = MagenPaths.listSchemeFiles("idea-macos")
      files should not be empty
      files.exists(_.contains("idea/")) shouldBe true
      files.exists(_.contains("vscode/")) shouldBe true
      files.exists(_.contains("generic/")) shouldBe true
    }

    "read a scheme file" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val content = MagenPaths.readSchemeFile("pshirshov", "search.yaml")
      content should include("mapping:")
      content should include("actions.find")
    }

    "read a negation file" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val content = MagenPaths.readNegationFile("idea/idea-all-actions.json")
      content should include("[")
      content.length should be > 100
    }

    "read vscode negation files" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val content = MagenPaths.readNegationFile("vscode/vscode-keymap-linux-!negate-all.json")
      content should include("[")
    }

    "fail on missing resource" in {
      MagenPaths.configure(MappingsSource.Classpath)
      an[AssertionError] should be thrownBy {
        MagenPaths.readMappingFile("nonexistent/file.yaml")
      }
    }

    "fail on missing scheme" in {
      MagenPaths.configure(MappingsSource.Classpath)
      an[AssertionError] should be thrownBy {
        MagenPaths.listSchemeFiles("nonexistent-scheme")
      }
    }

    "fail on writableDir in classpath mode" in {
      MagenPaths.configure(MappingsSource.Classpath)
      an[AssertionError] should be thrownBy {
        MagenPaths.writableDir
      }
    }

    "fail on missing negation file" in {
      MagenPaths.configure(MappingsSource.Classpath)
      an[AssertionError] should be thrownBy {
        MagenPaths.readNegationFile("nonexistent/file.json")
      }
    }
  }

  "MagenPaths in Filesystem mode" should {
    "list schemes from filesystem" in {
      val tmpDir = Files.createTempDirectory("magen-test-")
      try {
        Files.createDirectories(tmpDir.resolve("test-scheme"))
        Files.write(
          tmpDir.resolve("test-scheme").resolve("bindings.yaml"),
          "mapping: []".getBytes(StandardCharsets.UTF_8),
        )

        MagenPaths.configure(MappingsSource.Filesystem(tmpDir))
        val schemes = MagenPaths.listSchemes()
        schemes should contain("test-scheme")
      } finally {
        deleteRecursive(tmpDir)
      }
    }

    "list scheme files from filesystem" in {
      val tmpDir = Files.createTempDirectory("magen-test-")
      try {
        val schemeDir = tmpDir.resolve("my-scheme")
        val subDir    = schemeDir.resolve("sub")
        Files.createDirectories(subDir)
        Files.write(schemeDir.resolve("a.yaml"), "mapping: []".getBytes(StandardCharsets.UTF_8))
        Files.write(subDir.resolve("b.yaml"), "mapping: []".getBytes(StandardCharsets.UTF_8))

        MagenPaths.configure(MappingsSource.Filesystem(tmpDir))
        val files = MagenPaths.listSchemeFiles("my-scheme")
        files should have size 2
        files should contain("a.yaml")
        files.exists(_.endsWith("b.yaml")) shouldBe true
      } finally {
        deleteRecursive(tmpDir)
      }
    }

    "return writableDir in filesystem mode" in {
      val tmpDir = Files.createTempDirectory("magen-test-")
      try {
        MagenPaths.configure(MappingsSource.Filesystem(tmpDir))
        MagenPaths.writableDir shouldBe tmpDir
      } finally {
        deleteRecursive(tmpDir)
      }
    }

    "read files from filesystem" in {
      val tmpDir = Files.createTempDirectory("magen-test-")
      try {
        val schemeDir = tmpDir.resolve("fs-scheme")
        Files.createDirectories(schemeDir)
        Files.write(schemeDir.resolve("test.yaml"), "mapping:\n  - id: \"test\"".getBytes(StandardCharsets.UTF_8))

        MagenPaths.configure(MappingsSource.Filesystem(tmpDir))
        val content = MagenPaths.readSchemeFile("fs-scheme", "test.yaml")
        content should include("test")
      } finally {
        deleteRecursive(tmpDir)
      }
    }
  }

  "MagenPaths negation source" should {
    "read negation file from filesystem" in {
      val tmpDir = Files.createTempDirectory("magen-neg-test-")
      try {
        val ideaDir = tmpDir.resolve("idea")
        Files.createDirectories(ideaDir)
        Files.write(ideaDir.resolve("test-actions.json"), "[\"Action1\", \"Action2\"]".getBytes(StandardCharsets.UTF_8))

        MagenPaths.configureNegations(NegationsSource.Filesystem(tmpDir))
        val content = MagenPaths.readNegationFile("idea/test-actions.json")
        content should include("Action1")
        content should include("Action2")
      } finally {
        deleteRecursive(tmpDir)
      }
    }

    "fall back to classpath when negation file not on filesystem" in {
      val tmpDir = Files.createTempDirectory("magen-neg-test-")
      try {
        MagenPaths.configureNegations(NegationsSource.Filesystem(tmpDir))
        val content = MagenPaths.readNegationFile("idea/idea-all-actions.json")
        content should include("[")
        content.length should be > 100
      } finally {
        deleteRecursive(tmpDir)
      }
    }

    "prefer filesystem negation over classpath" in {
      val tmpDir = Files.createTempDirectory("magen-neg-test-")
      try {
        val ideaDir = tmpDir.resolve("idea")
        Files.createDirectories(ideaDir)
        val customContent = "[\"CustomAction\"]"
        Files.write(ideaDir.resolve("idea-all-actions.json"), customContent.getBytes(StandardCharsets.UTF_8))

        MagenPaths.configureNegations(NegationsSource.Filesystem(tmpDir))
        val content = MagenPaths.readNegationFile("idea/idea-all-actions.json")
        content shouldBe customContent
      } finally {
        deleteRecursive(tmpDir)
      }
    }
  }

  private def deleteRecursive(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).iterator().forEachRemaining(deleteRecursive)
    }
    Files.deleteIfExists(path)
  }
}
