package io.septimalmind.magen.importer

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EditorMappingsTest extends AnyWordSpec with Matchers {

  "EditorMappings.lookupFromIdea" should {
    "find vscode equivalent for known IDEA action" in {
      val eq = EditorMappings.lookupFromIdea("$Copy")
      eq.vscode shouldBe defined
      eq.vscode.get.action shouldBe "editor.action.clipboardCopyAction"
    }

    "find zed equivalent for known IDEA action" in {
      val eq = EditorMappings.lookupFromIdea("$Copy")
      eq.zed shouldBe defined
      eq.zed.get.action shouldBe "editor::Copy"
      eq.zed.get.context should contain("Editor")
    }

    "return empty equivalents for unknown IDEA action" in {
      val eq = EditorMappings.lookupFromIdea("NonExistentAction12345")
      eq.vscode shouldBe None
      eq.zed shouldBe None
    }

    "find equivalents for $Paste" in {
      val eq = EditorMappings.lookupFromIdea("$Paste")
      eq.vscode shouldBe defined
      eq.vscode.get.action shouldBe "editor.action.clipboardPasteAction"
      eq.zed shouldBe defined
      eq.zed.get.action shouldBe "editor::Paste"
    }

    "find equivalents for $Redo" in {
      val eq = EditorMappings.lookupFromIdea("$Redo")
      eq.vscode shouldBe defined
      eq.zed shouldBe defined
    }

    "return partial equivalents when only one editor mapped" in {
      val eq = EditorMappings.lookupFromIdea("$Delete")
      eq.vscode shouldBe defined
      eq.zed shouldBe defined
    }
  }

  "EditorMappings.lookupFromVscode" should {
    "find zed equivalent for known VSCode action without context" in {
      val eq = EditorMappings.lookupFromVscode("editor.action.clipboardCopyAction", List.empty)
      eq.zed shouldBe defined
      eq.zed.get.action shouldBe "editor::Copy"
    }

    "find idea equivalent for known VSCode action without context" in {
      val eq = EditorMappings.lookupFromVscode("redo", List.empty)
      eq.idea shouldBe defined
    }

    "return empty equivalents for unknown VSCode action" in {
      val eq = EditorMappings.lookupFromVscode("nonexistent.action.xyz", List.empty)
      eq.idea shouldBe None
      eq.zed shouldBe None
    }

    "find equivalents using composite key with context" in {
      val eq = EditorMappings.lookupFromVscode(
        "acceptRenameInput",
        List("editorFocus && renameInputVisible && !isComposing"),
      )
      eq.zed shouldBe defined
      eq.zed.get.action shouldBe "editor::ConfirmRename"
    }

    "fall back to action-only lookup when composite key not found" in {
      val eq = EditorMappings.lookupFromVscode("editor.action.clipboardCopyAction", List("someRandomContext"))
      eq.zed shouldBe defined
      eq.zed.get.action shouldBe "editor::Copy"
    }
  }

  "EditorMappings.lookupFromZed" should {
    "find vscode equivalent for known Zed action with context" in {
      val eq = EditorMappings.lookupFromZed("editor::ConfirmRename", List("Editor && renaming"))
      eq.vscode shouldBe defined
      eq.vscode.get.action shouldBe "acceptRenameInput"
    }

    "return empty equivalents for unknown Zed action" in {
      val eq = EditorMappings.lookupFromZed("nonexistent::Action", List.empty)
      eq.vscode shouldBe None
      eq.idea shouldBe None
    }

    "find equivalents for editor::Copy" in {
      val eq = EditorMappings.lookupFromZed("editor::Copy", List("Editor"))
      eq.vscode shouldBe defined
      eq.vscode.get.action shouldBe "editor.action.clipboardCopyAction"
    }
  }
}
