package io.septimalmind.magen.importer

import io.septimalmind.magen.model.Key.{KeyCombo, NamedKey}
import io.septimalmind.magen.model.{Chord, Modifier, SchemeId}
import io.septimalmind.magen.util.{MagenPaths, MappingsSource}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class SchemeWriterTest extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  private var tmpDir: Path = _

  override def beforeEach(): Unit = {
    tmpDir = Files.createTempDirectory("magen-writer-test-")
    MagenPaths.configure(MappingsSource.Filesystem(tmpDir))
  }

  override def afterEach(): Unit = {
    MagenPaths.configure(MappingsSource.Classpath)
    deleteRecursive(tmpDir)
  }

  private val ctrlC = Chord(List(KeyCombo(List(Modifier.Ctrl), NamedKey("KeyC"))))

  "SchemeWriter.write with IDEA import" should {
    "populate vscode and zed equivalents for $Copy" in {
      val imported = ImportedScheme(
        ImportSource.Idea,
        List(ImportedBinding("$Copy", ctrlC, List.empty)),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-idea"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("idea:")
      content should include("""action: "$Copy"""")
      content should include("vscode:")
      content should include("""action: "editor.action.clipboardCopyAction"""")
      content should include("zed:")
      content should include("""action: "editor::Copy"""")
      content should not include "missing: true"
    }

    "write missing: true for unmapped IDEA actions" in {
      val imported = ImportedScheme(
        ImportSource.Idea,
        List(ImportedBinding("SomeObscureIdeaAction", ctrlC, List.empty)),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-idea-unmapped"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("""action: "SomeObscureIdeaAction"""")
      content should include("missing: true")
    }

    "populate zed context when present in mappings" in {
      val imported = ImportedScheme(
        ImportSource.Idea,
        List(ImportedBinding("$Copy", ctrlC, List.empty)),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-idea-ctx"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("""context: ["Editor"]""")
    }
  }

  "SchemeWriter.write with VSCode import" should {
    "populate idea and zed equivalents for clipboard copy" in {
      val imported = ImportedScheme(
        ImportSource.VSCode,
        List(ImportedBinding("editor.action.clipboardCopyAction", ctrlC, List.empty)),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-vscode"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("vscode:")
      content should include("""action: "editor.action.clipboardCopyAction"""")
      content should include("zed:")
      content should include("""action: "editor::Copy"""")
    }

    "write missing: true for unmapped VSCode actions" in {
      val imported = ImportedScheme(
        ImportSource.VSCode,
        List(ImportedBinding("some.obscure.vscode.action", ctrlC, List.empty)),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-vscode-unmapped"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("""action: "some.obscure.vscode.action"""")
      content should include("missing: true")
    }
  }

  "SchemeWriter.write with Zed import" should {
    "populate idea and vscode equivalents for editor::Copy" in {
      val imported = ImportedScheme(
        ImportSource.Zed,
        List(ImportedBinding("editor::Copy", ctrlC, List("Editor"))),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-zed"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("zed:")
      content should include("""action: "editor::Copy"""")
      content should include("vscode:")
      content should include("""action: "editor.action.clipboardCopyAction"""")
    }

    "write missing: true for unmapped Zed actions" in {
      val imported = ImportedScheme(
        ImportSource.Zed,
        List(ImportedBinding("obscure::ZedAction", ctrlC, List.empty)),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-zed-unmapped"), imported)
      val content    = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)

      content should include("""action: "obscure::ZedAction"""")
      content should include("missing: true")
    }
  }

  "SchemeWriter.write statistics" should {
    "report mapped and unmapped counts" in {
      val imported = ImportedScheme(
        ImportSource.Idea,
        List(
          ImportedBinding("$Copy", ctrlC, List.empty),
          ImportedBinding("$Paste", ctrlC, List.empty),
          ImportedBinding("UnknownAction", ctrlC, List.empty),
        ),
      )
      val outputFile = SchemeWriter.write(SchemeId("test-stats"), imported)
      Files.exists(outputFile) shouldBe true
    }
  }

  private def deleteRecursive(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).iterator().forEachRemaining(deleteRecursive)
    }
    Files.deleteIfExists(path)
  }
}
