package io.septimalmind.magen.importer

import io.septimalmind.magen.model.Modifier
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class IdeaParentResolutionTest extends AnyWordSpec with Matchers {
  private var tmpDir: Path = _

  private def withTmpDir[T](f: => T): T = {
    tmpDir = Files.createTempDirectory("magen-parent-test-")
    try f
    finally deleteRecursive(tmpDir)
  }

  private def writeTempFile(name: String, content: String): Path = {
    val file = tmpDir.resolve(name)
    Files.write(file, content.getBytes(StandardCharsets.UTF_8))
    file
  }

  "IDEA parent resolution" should {

    "inherit bindings from $default parent" in withTmpDir {
      // Keymap with parent=$default that adds one action
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="ChildKeymap" parent="$default">
          |  <action id="MyCustomAction">
          |    <keyboard-shortcut first-keystroke="ctrl alt X" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("child.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      // Should have the custom action
      val custom = imported.bindings.filter(_.action == "MyCustomAction")
      custom should have size 1

      // Should also have inherited $default bindings like $Copy (ctrl C)
      val copy = imported.bindings.filter(_.action == "$Copy")
      copy should not be empty
      copy.exists { b =>
        b.chord.combos.head.modifiers.contains(Modifier.Ctrl) &&
        b.chord.combos.head.key.name == "C"
      } shouldBe true

      // Should have many more bindings than just the one defined
      imported.bindings.size should be > 100
    }

    "override parent binding with child binding" in withTmpDir {
      // $default has Find bound to ctrl F and alt F3
      // Mac OS X 10.5+ overrides Find to meta F
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="OverrideTest" parent="$default">
          |  <action id="Find">
          |    <keyboard-shortcut first-keystroke="meta F" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("override.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      val findBindings = imported.bindings.filter(_.action == "Find")
      // Should have exactly the child's override (meta F), not the parent's (ctrl F, alt F3)
      findBindings should have size 1
      findBindings.head.chord.combos.head.modifiers should contain(Modifier.Meta)
      findBindings.head.chord.combos.head.key.name shouldBe "F"
    }

    "unbind parent action when child has empty action element" in withTmpDir {
      // $default has EditorScrollUp bound to ctrl UP
      // An empty <action id="EditorScrollUp"/> should remove it
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="UnbindTest" parent="$default">
          |  <action id="EditorScrollUp"/>
          |</keymap>""".stripMargin

      val file = writeTempFile("unbind.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      // EditorScrollUp should NOT be in the result (unbound)
      val scrollUp = imported.bindings.filter(_.action == "EditorScrollUp")
      scrollUp shouldBe empty

      // But other $default bindings should still be present
      val copy = imported.bindings.filter(_.action == "$Copy")
      copy should not be empty
    }

    "resolve nested parents (3-level chain)" in withTmpDir {
      // Build: GrandChild → MiddleChild → $default
      // $default has MoveLineUp = alt shift UP
      // MiddleChild overrides Find = meta F and unbinds EditorScrollUp
      // GrandChild adds a custom action and overrides Find again

      val middleXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="MiddleChild" parent="$default">
          |  <action id="Find">
          |    <keyboard-shortcut first-keystroke="meta F" />
          |  </action>
          |  <action id="EditorScrollUp"/>
          |</keymap>""".stripMargin

      val grandChildXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="GrandChild" parent="MiddleChild">
          |  <action id="Find">
          |    <keyboard-shortcut first-keystroke="alt F" />
          |  </action>
          |  <action id="MyAction">
          |    <keyboard-shortcut first-keystroke="ctrl shift M" />
          |  </action>
          |</keymap>""".stripMargin

      // Write both files so MiddleChild can be found as a file-based parent
      writeTempFile("MiddleChild.xml", middleXml)
      writeTempFile("GrandChild.xml", grandChildXml)

      val imported = IdeaSchemeImporter.importFrom(
        "GrandChild",
        List(tmpDir),
      )

      // GrandChild's own action
      val myAction = imported.bindings.filter(_.action == "MyAction")
      myAction should have size 1

      // Find should be GrandChild's override (alt F), not MiddleChild (meta F) or $default (ctrl F)
      val find = imported.bindings.filter(_.action == "Find")
      find should have size 1
      find.head.chord.combos.head.modifiers should contain(Modifier.Alt)
      find.head.chord.combos.head.key.name shouldBe "F"

      // EditorScrollUp unbound by MiddleChild, should stay unbound
      val scrollUp = imported.bindings.filter(_.action == "EditorScrollUp")
      scrollUp shouldBe empty

      // MoveLineUp inherited from $default through the chain
      val moveUp = imported.bindings.filter(_.action == "MoveLineUp")
      moveUp should not be empty
    }

    "import real bundled keymap Mac OS X 10.5+ with parent resolution" in withTmpDir {
      // Create a minimal user keymap that inherits from Mac OS X 10.5+
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="MyMacKeymap" parent="Mac OS X 10.5+">
          |  <action id="CustomMacAction">
          |    <keyboard-shortcut first-keystroke="meta shift X" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("mymac.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      // Custom action present
      val custom = imported.bindings.filter(_.action == "CustomMacAction")
      custom should have size 1

      // Mac OS X 10.5+ overrides Find to meta F (not $default's ctrl F)
      val find = imported.bindings.filter(_.action == "Find")
      find should not be empty
      find.exists(b => b.chord.combos.head.modifiers.contains(Modifier.Meta)) shouldBe true

      // EditorScrollUp is unbound in Mac OS X 10.5+ (empty action element)
      val scrollUp = imported.bindings.filter(_.action == "EditorScrollUp")
      scrollUp shouldBe empty

      // $default bindings should be inherited through the chain
      imported.bindings.size should be > 100
    }

    "import real bundled Sublime Text (Mac OS X) with 3-level parent chain" in withTmpDir {
      // Sublime Text (Mac OS X) → Mac OS X 10.5+ → $default
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="MySublime" parent="Sublime Text (Mac OS X)">
          |  <action id="CustomSublimeAction">
          |    <keyboard-shortcut first-keystroke="meta alt S" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("mysublime.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      // Custom action present
      val custom = imported.bindings.filter(_.action == "CustomSublimeAction")
      custom should have size 1

      // Should have many inherited bindings from the full chain
      imported.bindings.size should be > 100

      // EditorScrollUp was unbound at Mac OS X 10.5+ level, but
      // Sublime Text (Mac OS X) re-binds it to ctrl alt UP
      val scrollUp = imported.bindings.filter(_.action == "EditorScrollUp")
      scrollUp should have size 1
      scrollUp.head.chord.combos.head.modifiers should contain allOf(Modifier.Ctrl, Modifier.Alt)
    }

    "import Default for GNOME with 3-level parent chain" in withTmpDir {
      // Default for GNOME → Default for XWin → $default
      // Default for GNOME unbinds MoveLineUp and MoveLineDown
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="MyGnome" parent="Default for GNOME">
          |  <action id="CustomGnomeAction">
          |    <keyboard-shortcut first-keystroke="ctrl alt G" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("mygnome.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      // Custom action
      val custom = imported.bindings.filter(_.action == "CustomGnomeAction")
      custom should have size 1

      // MoveLineUp unbound by Default for GNOME
      val moveUp = imported.bindings.filter(_.action == "MoveLineUp")
      moveUp shouldBe empty

      // MoveLineDown also unbound by Default for GNOME
      val moveDown = imported.bindings.filter(_.action == "MoveLineDown")
      moveDown shouldBe empty

      // $default bindings inherited through the chain
      val copy = imported.bindings.filter(_.action == "$Copy")
      copy should not be empty
    }

    "warn and return only own bindings when parent is unknown" in withTmpDir {
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="OrphanKeymap" parent="NonExistentParent">
          |  <action id="MyAction">
          |    <keyboard-shortcut first-keystroke="ctrl A" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("orphan.xml", xml)

      // Capture stdout to verify warning
      val output = new java.io.ByteArrayOutputStream()
      val imported = Console.withOut(output) {
        IdeaSchemeImporter.importFromFile(file)
      }

      val stdout = output.toString
      stdout should include("WARNING")
      stdout should include("NonExistentParent")

      // Should only have its own binding
      imported.bindings should have size 1
      imported.bindings.head.action shouldBe "MyAction"
    }

    "import keymap with no parent (root keymap)" in withTmpDir {
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="StandaloneKeymap">
          |  <action id="ActionA">
          |    <keyboard-shortcut first-keystroke="ctrl A" />
          |  </action>
          |  <action id="ActionB">
          |    <keyboard-shortcut first-keystroke="ctrl B" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("standalone.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      imported.bindings should have size 2
      imported.bindings.map(_.action).toSet shouldBe Set("ActionA", "ActionB")
    }

    "child with multiple shortcuts for same action replaces all parent shortcuts" in withTmpDir {
      // $default has $Copy = ctrl C, ctrl INSERT (two shortcuts)
      // Child overrides with just meta C
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<keymap version="1" name="ReplaceTest" parent="$default">
          |  <action id="$Copy">
          |    <keyboard-shortcut first-keystroke="meta C" />
          |  </action>
          |</keymap>""".stripMargin

      val file = writeTempFile("replace.xml", xml)
      val imported = IdeaSchemeImporter.importFromFile(file)

      val copy = imported.bindings.filter(_.action == "$Copy")
      // Should only have meta C, not ctrl C or ctrl INSERT from parent
      copy should have size 1
      copy.head.chord.combos.head.modifiers should contain(Modifier.Meta)
      copy.head.chord.combos.head.key.name shouldBe "C"
    }
  }

  private def deleteRecursive(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).iterator().forEachRemaining(deleteRecursive)
    }
    Files.deleteIfExists(path)
  }
}
