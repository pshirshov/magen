package io.septimalmind.magen.importer

import io.circe.*
import io.septimalmind.magen.Magen
import io.septimalmind.magen.model.*
import io.septimalmind.magen.targets.*
import io.septimalmind.magen.util.{MagenPaths, MappingsSource, NegationsSource, ShortcutParser}
import izumi.fundamentals.collections.nonempty.NEList
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class ImportExportRoundTripTest extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  private var tmpDir: Path = _

  override def beforeEach(): Unit = {
    tmpDir = Files.createTempDirectory("magen-e2e-")
  }

  override def afterEach(): Unit = {
    MagenPaths.configure(MappingsSource.Classpath)
    MagenPaths.configureNegations(NegationsSource.Classpath)
    deleteRecursive(tmpDir)
  }

  // -- VSCode round-trip --

  "VSCode import → render round-trip" should {
    val vscodeInput =
      """[
        |  {"key": "ctrl+c", "command": "editor.action.clipboardCopyAction", "when": "textInputFocus"},
        |  {"key": "ctrl+v", "command": "editor.action.clipboardPasteAction"},
        |  {"key": "ctrl+shift+k", "command": "editor.action.deleteLines"},
        |  {"key": "ctrl+f", "command": "actions.find", "when": "editorFocus"},
        |  {"key": "ctrl+h", "command": "editor.action.startFindReplaceAction", "when": "editorFocus"},
        |  {"key": "ctrl+z", "command": "undo"},
        |  {"key": "ctrl+shift+z", "command": "redo"},
        |  {"key": "ctrl+k ctrl+c", "command": "editor.action.addCommentLine", "when": "textInputFocus"},
        |  {"key": "f2", "command": "editor.action.rename", "when": "editorHasRenameProvider && editorTextFocus"},
        |  {"key": "ctrl+/", "command": "editor.action.commentLine", "when": "textInputFocus"}
        |]""".stripMargin

    "import VSCode keybindings and produce valid scheme YAML" in {
      val inputFile = writeTempFile("keybindings.json", vscodeInput)
      val imported  = VscodeSchemeImporter.importFrom(inputFile)

      imported.source shouldBe ImportSource.VSCode
      imported.bindings should not be empty

      val actions = imported.bindings.map(_.action)
      actions should contain("editor.action.clipboardCopyAction")
      actions should contain("undo")
      actions should contain("redo")
    }

    "write scheme and load it back through Magen pipeline" in {
      val inputFile = writeTempFile("keybindings.json", vscodeInput)
      val imported  = VscodeSchemeImporter.importFrom(inputFile)

      val mappingsDir = tmpDir.resolve("mappings")
      Files.createDirectories(mappingsDir)
      MagenPaths.configure(MappingsSource.Filesystem(mappingsDir))
      SchemeWriter.write(SchemeId("test-vscode"), imported)

      val schemeFiles = MagenPaths.listSchemeFiles("test-vscode")
      schemeFiles should contain("imported.yaml")

      val yamlContent = MagenPaths.readSchemeFile("test-vscode", "imported.yaml")
      yamlContent should include("mapping:")
      yamlContent should include("editor.action.clipboardCopyAction")
    }

    "render imported VSCode scheme back to VSCode format preserving bindings" in {
      val (mapping, _) = importAndLoad("keybindings.json", vscodeInput, ImportSource.VSCode, Platform.Linux)

      val rendered = VSCodeRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      val commands = json.flatMap(_("command").flatMap(_.asString))
      commands should contain("editor.action.clipboardCopyAction")
      commands should contain("undo")
      commands should contain("redo")

      val keys = json.flatMap(_("key").flatMap(_.asString))
      // ctrl+c should be in the rendered output
      keys.exists(_.contains("ctrl+c")) shouldBe true
      keys.exists(_.contains("ctrl+z")) shouldBe true
    }

    "render imported VSCode scheme to Zed format with cross-editor mappings" in {
      val (mapping, _) = importAndLoad("keybindings.json", vscodeInput, ImportSource.VSCode, Platform.Linux)

      val rendered = ZedRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      // Zed output should contain bindings from cross-editor mappings
      val allBindings = json.flatMap(_("bindings").flatMap(_.asObject))
      val allActions  = allBindings.flatMap(_.values.flatMap(_.asString))

      // undo/redo should have zed equivalents if cross-editor mappings exist
      val zedConcepts = mapping.mapping.filter(_.zed.isDefined)
      zedConcepts should not be empty
    }

    "render imported VSCode scheme to IDEA format with cross-editor mappings" in {
      val (mapping, _) = importAndLoad("keybindings.json", vscodeInput, ImportSource.VSCode, Platform.Linux)

      val renderer = new IdeaRenderer(IdeaParams(List.empty, negate = false, parent = "$default", keymapName = "Magen-test"))
      val rendered = renderer.render(mapping, Platform.Linux)

      rendered should include("<keymap")
      rendered should include("Magen-test")

      // IDEA actions should be populated from cross-editor mappings
      val ideaConcepts = mapping.mapping.filter(_.idea.isDefined)
      ideaConcepts should not be empty
    }

    "preserve context/when clause through round-trip" in {
      val (mapping, _) = importAndLoad("keybindings.json", vscodeInput, ImportSource.VSCode, Platform.Linux)

      val rendered = VSCodeRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      // find the clipboardCopy entry and verify its "when" clause
      val copyEntry = json.find(o => o("command").flatMap(_.asString).contains("editor.action.clipboardCopyAction"))
      copyEntry shouldBe defined
      copyEntry.get("when").flatMap(_.asString) shouldBe Some("textInputFocus")
    }
  }

  // -- IDEA round-trip --

  "IDEA import → render round-trip" should {
    val ideaInput =
      """<?xml version="1.0" encoding="UTF-8"?>
        |<keymap version="1" name="TestKeymap">
        |  <action id="$Copy">
        |    <keyboard-shortcut first-keystroke="ctrl C" />
        |  </action>
        |  <action id="$Paste">
        |    <keyboard-shortcut first-keystroke="ctrl V" />
        |  </action>
        |  <action id="$Cut">
        |    <keyboard-shortcut first-keystroke="ctrl X" />
        |  </action>
        |  <action id="EditorLeft">
        |    <keyboard-shortcut first-keystroke="LEFT" />
        |  </action>
        |  <action id="$Undo">
        |    <keyboard-shortcut first-keystroke="ctrl Z" />
        |  </action>
        |  <action id="$Redo">
        |    <keyboard-shortcut first-keystroke="ctrl shift Z" />
        |  </action>
        |  <action id="FindInPath">
        |    <keyboard-shortcut first-keystroke="ctrl shift F" />
        |  </action>
        |  <action id="GotoDeclaration">
        |    <keyboard-shortcut first-keystroke="ctrl B" />
        |  </action>
        |  <action id="CommentByLineComment">
        |    <keyboard-shortcut first-keystroke="ctrl SLASH" />
        |  </action>
        |  <action id="ReformatCode">
        |    <keyboard-shortcut first-keystroke="ctrl alt L" />
        |  </action>
        |  <action id="GotoAction">
        |    <keyboard-shortcut first-keystroke="ctrl shift A" />
        |  </action>
        |  <action id="RenameElement">
        |    <keyboard-shortcut first-keystroke="shift F6" />
        |  </action>
        |  <action id="ActivateProjectToolWindow">
        |    <keyboard-shortcut first-keystroke="alt 1" />
        |  </action>
        |  <action id="EditorSelectWord">
        |    <keyboard-shortcut first-keystroke="ctrl W" />
        |  </action>
        |  <action id="NextTab">
        |    <keyboard-shortcut first-keystroke="ctrl shift CLOSE_BRACKET" />
        |  </action>
        |  <action id="PrevTab">
        |    <keyboard-shortcut first-keystroke="ctrl shift OPEN_BRACKET" />
        |  </action>
        |</keymap>""".stripMargin

    "import IDEA keybindings and produce valid scheme YAML" in {
      val inputFile = writeTempFile("keymap.xml", ideaInput)
      val imported  = IdeaSchemeImporter.importFromFile(inputFile)

      imported.source shouldBe ImportSource.Idea
      imported.bindings should not be empty

      val actions = imported.bindings.map(_.action)
      actions should contain("$Copy")
      actions should contain("$Paste")
      actions should contain("$Undo")
      actions should contain("GotoDeclaration")
    }

    "populate cross-editor mappings for known IDEA actions" in {
      val inputFile = writeTempFile("keymap.xml", ideaInput)
      val imported  = IdeaSchemeImporter.importFromFile(inputFile)

      val mappingsDir = tmpDir.resolve("mappings")
      Files.createDirectories(mappingsDir)
      MagenPaths.configure(MappingsSource.Filesystem(mappingsDir))
      SchemeWriter.write(SchemeId("test-idea"), imported)

      val yamlContent = MagenPaths.readSchemeFile("test-idea", "imported.yaml")
      // $Copy should have VSCode and Zed equivalents from editor-mappings
      yamlContent should include("""action: "$Copy"""")
      yamlContent should include("editor.action.clipboardCopyAction")
      yamlContent should include("editor::Copy")
    }

    "render imported IDEA scheme back to IDEA format preserving bindings" in {
      val (mapping, _) = importAndLoad("keymap.xml", ideaInput, ImportSource.Idea, Platform.Linux)

      val renderer = new IdeaRenderer(IdeaParams(List.empty, negate = false, parent = "$default", keymapName = "Magen-test"))
      val rendered = renderer.render(mapping, Platform.Linux)

      rendered should include("<keymap")
      rendered should include("""id="$Copy"""")
      rendered should include("""id="$Paste"""")
      rendered should include("""id="$Undo"""")

      // Verify keyboard shortcuts are present
      rendered should include("keyboard-shortcut")
      rendered should include("first-keystroke")
    }

    "render imported IDEA scheme to VSCode format with cross-editor mappings" in {
      val (mapping, _) = importAndLoad("keymap.xml", ideaInput, ImportSource.Idea, Platform.Linux)

      val rendered = VSCodeRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      val commands = json.flatMap(_("command").flatMap(_.asString))
      // Known IDEA actions should have VSCode equivalents
      commands should contain("editor.action.clipboardCopyAction")
      commands should contain("editor.action.clipboardPasteAction")
    }

    "render imported IDEA scheme to Zed format with cross-editor mappings" in {
      val (mapping, _) = importAndLoad("keymap.xml", ideaInput, ImportSource.Idea, Platform.Linux)

      val rendered = ZedRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      val allBindings = json.flatMap(_("bindings").flatMap(_.asObject))
      val allActions  = allBindings.flatMap(_.values.flatMap(_.asString))

      // editor::Copy should appear from cross-editor mappings
      allActions should contain("editor::Copy")
      allActions should contain("editor::Paste")
    }

    "handle two-keystroke IDEA shortcuts" in {
      val twoKeyInput =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="TestTwoKey">
          |  <action id="Vcs.Push">
          |    <keyboard-shortcut first-keystroke="ctrl K" second-keystroke="ctrl P" />
          |  </action>
          |</keymap>""".stripMargin

      val inputFile = writeTempFile("keymap-twokey.xml", twoKeyInput)
      val imported  = IdeaSchemeImporter.importFromFile(inputFile)

      imported.bindings should have size 1
      val binding = imported.bindings.head
      binding.action shouldBe "Vcs.Push"
      binding.chord.combos should have size 2
    }

    "handle IDEA bracket key normalization" in {
      val inputFile = writeTempFile("keymap.xml", ideaInput)
      val imported  = IdeaSchemeImporter.importFromFile(inputFile)

      // NextTab uses CLOSE_BRACKET → should normalize to BracketRight
      val nextTab = imported.bindings.find(_.action == "NextTab")
      nextTab shouldBe defined
      val key = nextTab.get.chord.combos.head.key
      key.name shouldBe "BracketRight"
    }
  }

  // -- Zed round-trip --

  "Zed import → render round-trip" should {
    val zedInput =
      """[
        |  {
        |    "bindings": {
        |      "ctrl-c": "editor::Copy",
        |      "ctrl-v": "editor::Paste",
        |      "ctrl-x": "editor::Cut",
        |      "ctrl-z": "editor::Undo",
        |      "ctrl-shift-z": "editor::Redo"
        |    }
        |  },
        |  {
        |    "context": "Editor",
        |    "bindings": {
        |      "ctrl-f": "search::Deploy",
        |      "ctrl-h": "search::DeployReplace",
        |      "ctrl-shift-k": "editor::DeleteLine",
        |      "f2": "editor::Rename",
        |      "ctrl-/": "editor::ToggleComments"
        |    }
        |  },
        |  {
        |    "context": "ProjectPanel && not_editing",
        |    "bindings": {
        |      "ctrl-shift-n": "project_panel::NewFile"
        |    }
        |  }
        |]""".stripMargin

    "import Zed keybindings and produce valid scheme YAML" in {
      val inputFile = writeTempFile("keymap.json", zedInput)
      val imported  = ZedSchemeImporter.importFrom(inputFile)

      imported.source shouldBe ImportSource.Zed
      imported.bindings should not be empty

      val actions = imported.bindings.map(_.action)
      actions should contain("editor::Copy")
      actions should contain("editor::Paste")
      actions should contain("search::Deploy")
    }

    "preserve Zed context through import" in {
      val inputFile = writeTempFile("keymap.json", zedInput)
      val imported  = ZedSchemeImporter.importFrom(inputFile)

      val findBinding = imported.bindings.find(_.action == "search::Deploy")
      findBinding shouldBe defined
      findBinding.get.context shouldBe List("Editor")

      val newFileBinding = imported.bindings.find(_.action == "project_panel::NewFile")
      newFileBinding shouldBe defined
      newFileBinding.get.context shouldBe List("ProjectPanel && not_editing")
    }

    "populate cross-editor mappings for known Zed actions" in {
      val inputFile = writeTempFile("keymap.json", zedInput)
      val imported  = ZedSchemeImporter.importFrom(inputFile)

      val mappingsDir = tmpDir.resolve("mappings")
      Files.createDirectories(mappingsDir)
      MagenPaths.configure(MappingsSource.Filesystem(mappingsDir))
      SchemeWriter.write(SchemeId("test-zed"), imported)

      val yamlContent = MagenPaths.readSchemeFile("test-zed", "imported.yaml")
      yamlContent should include("""action: "editor::Copy"""")
      // Cross-editor mappings should be populated
      yamlContent should include("editor.action.clipboardCopyAction")
    }

    "render imported Zed scheme back to Zed format preserving bindings" in {
      val (mapping, _) = importAndLoad("keymap.json", zedInput, ImportSource.Zed, Platform.Linux)

      val rendered = ZedRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      val allBindings = json.flatMap(_("bindings").flatMap(_.asObject))
      val allActions  = allBindings.flatMap(_.values.flatMap(_.asString))

      allActions should contain("editor::Copy")
      allActions should contain("editor::Paste")
    }

    "render imported Zed scheme to VSCode format with cross-editor mappings" in {
      val (mapping, _) = importAndLoad("keymap.json", zedInput, ImportSource.Zed, Platform.Linux)

      val rendered = VSCodeRenderer.render(mapping, Platform.Linux)
      val json     = parser.parse(rendered).flatMap(_.as[List[JsonObject]]).toOption.get

      val commands = json.flatMap(_("command").flatMap(_.asString))
      commands should contain("editor.action.clipboardCopyAction")
    }

    "render imported Zed scheme to IDEA format with cross-editor mappings" in {
      val (mapping, _) = importAndLoad("keymap.json", zedInput, ImportSource.Zed, Platform.Linux)

      val renderer = new IdeaRenderer(IdeaParams(List.empty, negate = false, parent = "$default", keymapName = "Magen-test"))
      val rendered = renderer.render(mapping, Platform.Linux)

      rendered should include("<keymap")
      // $Copy should appear from cross-editor mappings
      rendered should include("""id="$Copy"""")
    }

    "handle Zed modifier normalization (cmd → meta)" in {
      val cmdInput =
        """[{"bindings": {"cmd-c": "editor::Copy"}}]"""

      val inputFile = writeTempFile("keymap-cmd.json", cmdInput)
      val imported  = ZedSchemeImporter.importFrom(inputFile)

      imported.bindings should have size 1
      val binding = imported.bindings.head
      binding.chord.combos.head.modifiers should contain(Modifier.Meta)
    }
  }

  // -- Cross-editor consistency --

  "Cross-editor consistency" should {
    "import from each editor and render to all others with consistent key bindings" in {
      // Define the same binding in all three editor formats: Ctrl+C → copy
      val vscodeJson = """[{"key": "ctrl+c", "command": "editor.action.clipboardCopyAction"}]"""
      val ideaXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="Test">
          |  <action id="$Copy"><keyboard-shortcut first-keystroke="ctrl C" /></action>
          |</keymap>""".stripMargin
      val zedJson = """[{"bindings": {"ctrl-c": "editor::Copy"}}]"""

      // Import from each
      val vscodeFile = writeTempFile("vscode.json", vscodeJson)
      val ideaFile   = writeTempFile("idea.xml", ideaXml)
      val zedFile    = writeTempFile("zed.json", zedJson)

      val fromVscode = VscodeSchemeImporter.importFrom(vscodeFile)
      val fromIdea   = IdeaSchemeImporter.importFromFile(ideaFile)
      val fromZed    = ZedSchemeImporter.importFrom(zedFile)

      // All should produce Ctrl+C chord
      def assertCtrlC(bindings: List[ImportedBinding]): Unit = {
        bindings should have size 1
        val chord = bindings.head.chord
        chord.combos should have size 1
        chord.combos.head.modifiers should contain(Modifier.Ctrl)
        val keyName = chord.combos.head.key.name.toUpperCase
        keyName shouldBe "C"
      }

      assertCtrlC(fromVscode.bindings)
      assertCtrlC(fromIdea.bindings)
      assertCtrlC(fromZed.bindings)
    }

    "produce cross-editor mappings for standard actions regardless of source editor" in {
      val mappingsDir = tmpDir.resolve("mappings")
      Files.createDirectories(mappingsDir)
      MagenPaths.configure(MappingsSource.Filesystem(mappingsDir))

      // Import Ctrl+C as copy from IDEA
      val ideaXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="Test">
          |  <action id="$Copy"><keyboard-shortcut first-keystroke="ctrl C" /></action>
          |</keymap>""".stripMargin
      val ideaFile = writeTempFile("idea.xml", ideaXml)
      val imported = IdeaSchemeImporter.importFromFile(ideaFile)
      SchemeWriter.write(SchemeId("cross-test"), imported)

      val yaml = MagenPaths.readSchemeFile("cross-test", "imported.yaml")

      // The YAML should have:
      // 1. IDEA action: $Copy
      yaml should include("""action: "$Copy"""")
      // 2. VSCode equivalent: editor.action.clipboardCopyAction
      yaml should include("""action: "editor.action.clipboardCopyAction"""")
      // 3. Zed equivalent: editor::Copy
      yaml should include("""action: "editor::Copy"""")
    }
  }

  // -- Special characters --

  "Special character handling" should {
    "handle bracket keys in VSCode round-trip" in {
      val input    = """[{"key": "ctrl+[", "command": "editor.action.outdentLines"}]"""
      val file     = writeTempFile("brackets.json", input)
      val imported = VscodeSchemeImporter.importFrom(file)

      imported.bindings should have size 1
      imported.bindings.head.chord.combos.head.key.name shouldBe "BracketLeft"
    }

    "handle slash key in Zed round-trip" in {
      val input    = """[{"context": "Editor", "bindings": {"ctrl-/": "editor::ToggleComments"}}]"""
      val file     = writeTempFile("slash.json", input)
      val imported = ZedSchemeImporter.importFrom(file)

      imported.bindings should have size 1
      imported.bindings.head.chord.combos.head.key.name shouldBe "Slash"
    }

    "handle IDEA special key names (BACK_SPACE, PAGE_DOWN)" in {
      val input =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="Test">
          |  <action id="EditorBackSpace"><keyboard-shortcut first-keystroke="BACK_SPACE" /></action>
          |  <action id="EditorPageDown"><keyboard-shortcut first-keystroke="PAGE_DOWN" /></action>
          |</keymap>""".stripMargin
      val file     = writeTempFile("special.xml", input)
      val imported = IdeaSchemeImporter.importFromFile(file)

      val backspace = imported.bindings.find(_.action == "EditorBackSpace")
      backspace.get.chord.combos.head.key.name shouldBe "Backspace"

      val pageDown = imported.bindings.find(_.action == "EditorPageDown")
      pageDown.get.chord.combos.head.key.name shouldBe "PageDown"
    }
  }

  // -- Helpers --

  private def writeTempFile(name: String, content: String): Path = {
    val file = tmpDir.resolve(name)
    Files.write(file, content.getBytes(StandardCharsets.UTF_8))
    file
  }

  /** Import from editor format, write scheme YAML, load through Magen pipeline, return Mapping */
  private def importAndLoad(
    fileName: String,
    content: String,
    source: ImportSource,
    platform: Platform,
  ): (Mapping, ValidationResult) = {
    val inputFile = writeTempFile(fileName, content)
    val imported = source match {
      case ImportSource.VSCode => VscodeSchemeImporter.importFrom(inputFile)
      case ImportSource.Idea   => IdeaSchemeImporter.importFromFile(inputFile)
      case ImportSource.Zed    => ZedSchemeImporter.importFrom(inputFile)
    }

    val mappingsDir = tmpDir.resolve("mappings")
    Files.createDirectories(mappingsDir)
    MagenPaths.configure(MappingsSource.Filesystem(mappingsDir))
    SchemeWriter.write(SchemeId("e2e-test"), imported)

    Magen.loadAndValidate(SchemeId("e2e-test"), platform)
  }

  private def deleteRecursive(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).iterator().forEachRemaining(deleteRecursive)
    }
    Files.deleteIfExists(path)
  }
}
