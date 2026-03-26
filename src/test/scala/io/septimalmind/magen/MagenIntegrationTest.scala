package io.septimalmind.magen

import io.septimalmind.magen.model.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class MagenIntegrationTest extends AnyWordSpec with Matchers {

  private def withTempYaml(content: String)(f: Path => Unit): Unit = {
    val tmpFile = Files.createTempFile("magen-test-", ".yaml")
    try {
      Files.write(tmpFile, content.getBytes(StandardCharsets.UTF_8))
      f(tmpFile)
    } finally {
      Files.deleteIfExists(tmpFile)
    }
  }

  "Magen.readMapping" should {
    "parse YAML with universal bindings" in withTempYaml(
      """mapping:
        |  - id: "cursorLeft"
        |    binding:
        |      - "left"
        |    vscode:
        |      action: 'cursorLeft'
        |    idea:
        |      action: 'EditorLeft'
        |    zed:
        |      action: "editor::MoveLeft"
        |      context: [ "Editor" ]
        |""".stripMargin
    ) { path =>
      val raw = Magen.readMapping(path)
      raw.mapping.get should have size 1
      val concept = raw.mapping.get.head
      concept.id shouldBe "cursorLeft"
      concept.binding shouldBe PlatformBinding.Universal(List("left"))
    }

    "parse YAML with platform-specific bindings" in withTempYaml(
      """mapping:
        |  - id: "paste"
        |    binding:
        |      macos: "meta+[KeyV]"
        |      default: "ctrl+[KeyV]"
        |    vscode:
        |      action: 'editor.action.clipboardPasteAction'
        |    idea:
        |      action: '$Paste'
        |    zed:
        |      action: "editor::Paste"
        |      context: [ "Editor" ]
        |""".stripMargin
    ) { path =>
      val raw = Magen.readMapping(path)
      val concept = raw.mapping.get.head
      concept.binding shouldBe a[PlatformBinding.PerPlatform]
      val perPlatform = concept.binding.asInstanceOf[PlatformBinding.PerPlatform]
      perPlatform.macos shouldBe Some(List("meta+[KeyV]"))
      perPlatform.default shouldBe Some(List("ctrl+[KeyV]"))
      perPlatform.linux shouldBe None
      perPlatform.win shouldBe None
    }

    "parse YAML with single string binding" in withTempYaml(
      """mapping:
        |  - id: "simple"
        |    binding: "alt+left"
        |    vscode:
        |      action: 'test'
        |    idea:
        |      action: 'Test'
        |    zed:
        |      action: "editor::Test"
        |""".stripMargin
    ) { path =>
      val raw = Magen.readMapping(path)
      val concept = raw.mapping.get.head
      concept.binding shouldBe PlatformBinding.Universal(List("alt+left"))
    }

    "parse YAML with platform-specific list bindings" in withTempYaml(
      """mapping:
        |  - id: "multiPlatformList"
        |    binding:
        |      macos:
        |        - "meta+[KeyC]"
        |        - "meta+shift+[KeyC]"
        |      default:
        |        - "ctrl+[KeyC]"
        |    vscode:
        |      action: 'copy'
        |    idea:
        |      action: '$Copy'
        |    zed:
        |      action: "editor::Copy"
        |""".stripMargin
    ) { path =>
      val raw = Magen.readMapping(path)
      val concept = raw.mapping.get.head
      val perPlatform = concept.binding.asInstanceOf[PlatformBinding.PerPlatform]
      perPlatform.macos shouldBe Some(List("meta+[KeyC]", "meta+shift+[KeyC]"))
      perPlatform.default shouldBe Some(List("ctrl+[KeyC]"))
    }
  }

  "Magen.expandTemplate" should {
    "expand variables in platform-resolved bindings" in {
      val vars = Map("group.nav" -> "ctrl+[KeyN]")
      Magen.expandTemplate("${group.nav} [KeyG]", vars) shouldBe "ctrl+[KeyN] [KeyG]"
    }
  }

  "Full pipeline" should {
    "produce different output per platform for platform-specific bindings" in withTempYaml(
      """mapping:
        |  - id: "paste"
        |    binding:
        |      macos: "meta+[KeyV]"
        |      linux: "ctrl+[KeyV]"
        |      win: "ctrl+[KeyV]"
        |    vscode:
        |      action: 'editor.action.clipboardPasteAction'
        |      context: [ "textInputFocus" ]
        |    idea:
        |      action: '$Paste'
        |    zed:
        |      action: "editor::Paste"
        |      context: [ "Editor" ]
        |""".stripMargin
    ) { path =>
      val raw = Magen.readMapping(path)

      import io.septimalmind.magen.targets.*
      import io.septimalmind.magen.util.ShortcutParser
      import izumi.fundamentals.collections.nonempty.NEList

      def convertForPlatform(platform: Platform): Mapping = {
        val concepts = raw.mapping.get.flatMap { c =>
          val resolved = c.binding.resolve(platform)
          val v = c.vscode.flatMap(i => i.action.map(a =>
            VSCodeAction(a, i.context.toList.flatten, List.empty)
          ))
          val i = c.idea.flatMap(i => i.action.map(a => IdeaAction(a, i.mouse.toList.flatten)))
          val z = c.zed.flatMap(i => i.action.map(a => ZedAction(a, i.context.toList.flatten)))
          if (resolved.nonEmpty) {
            val chord = NEList.unsafeFrom(resolved).map(ShortcutParser.parseUnsafe)
            Seq(Concept(c.id, chord, i, v, z))
          } else Seq.empty
        }
        Mapping(concepts)
      }

      val macMapping = convertForPlatform(Platform.MacOS)
      val linuxMapping = convertForPlatform(Platform.Linux)

      val macVscode = VSCodeRenderer.render(macMapping, Platform.MacOS)
      val linuxVscode = VSCodeRenderer.render(linuxMapping, Platform.Linux)

      macVscode should include("meta+v")
      linuxVscode should include("ctrl+v")
      macVscode should not equal linuxVscode
    }
  }
}
