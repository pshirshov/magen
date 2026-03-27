package io.septimalmind.magen.targets

import io.circe.*
import io.circe.parser.parse as jsonParse
import io.septimalmind.magen.model.*
import io.septimalmind.magen.util.ShortcutParser
import izumi.fundamentals.collections.nonempty.NEList
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class RendererPlatformTest extends AnyWordSpec with Matchers {

  private val yamlWithPlatformBindings =
    """mapping:
      |  - id: "paste"
      |    binding:
      |      macos: "meta+[KeyV]"
      |      default: "ctrl+[KeyV]"
      |    vscode:
      |      action: 'editor.action.clipboardPasteAction'
      |      context: [ "textInputFocus" ]
      |    idea:
      |      action: '$Paste'
      |    zed:
      |      action: "editor::Paste"
      |      context: [ "Editor" ]
      |""".stripMargin

  private val yamlWithUniversalBindings =
    """mapping:
      |  - id: "cursorLeft"
      |    binding:
      |      - "left"
      |    vscode:
      |      action: 'cursorLeft'
      |      context: [ "textInputFocus" ]
      |    idea:
      |      action: 'EditorLeft'
      |    zed:
      |      action: "editor::MoveLeft"
      |      context: [ "Editor" ]
      |""".stripMargin

  private def loadFromYaml(yamlStr: String, platform: Platform): Mapping = {
    import io.circe.generic.auto.*
    import io.circe.yaml

    val raw = yaml.v12.parser
      .parse(yamlStr)
      .flatMap(_.as[RawMapping])
      .toOption
      .get

    val allConcepts = raw.mapping.toSeq.flatten
    val concepts = allConcepts.flatMap {
      c =>
        val resolvedBinding = c.binding.resolve(platform)
        val i               = c.idea.flatMap(i => i.action.map(a => IdeaAction(a, i.mouse.toList.flatten)))
        val v = c.vscode.flatMap(
          i =>
            i.action.map {
              a =>
                val bindings = i.binding.toList.flatten.map(ShortcutParser.parseUnsafe)
                VSCodeAction(a, i.context.toList.flatten, bindings)
            }
        )
        val z = c.zed.flatMap(i => i.action.map(a => ZedAction(a, i.context.toList.flatten)))

        if (resolvedBinding.nonEmpty) {
          val chord = NEList.unsafeFrom(resolvedBinding).map(ShortcutParser.parseUnsafe)
          Seq(Concept(c.id, chord, i, v, z))
        } else {
          Seq.empty
        }
    }
    Mapping(concepts.toList)
  }

  private def findVscodeBinding(jsonStr: String, command: String): Option[String] = {
    val arr = jsonParse(jsonStr).flatMap(_.as[List[JsonObject]]).toOption.get
    arr
      .find(_.apply("command").flatMap(_.asString).contains(command))
      .flatMap(_.apply("key").flatMap(_.asString))
  }

  "VSCodeRenderer" should {
    "render platform-specific bindings for macOS" in {
      val mapping = loadFromYaml(yamlWithPlatformBindings, Platform.MacOS)
      val result  = VSCodeRenderer.render(mapping, Platform.MacOS)
      val key     = findVscodeBinding(result, "editor.action.clipboardPasteAction")
      key shouldBe Some("meta+v")
    }

    "render platform-specific bindings for Linux (default fallback)" in {
      val mapping = loadFromYaml(yamlWithPlatformBindings, Platform.Linux)
      val result  = VSCodeRenderer.render(mapping, Platform.Linux)
      val key     = findVscodeBinding(result, "editor.action.clipboardPasteAction")
      key shouldBe Some("ctrl+v")
    }

    "render universal bindings the same for all platforms" in {
      val macMapping   = loadFromYaml(yamlWithUniversalBindings, Platform.MacOS)
      val linuxMapping = loadFromYaml(yamlWithUniversalBindings, Platform.Linux)
      val macKey = findVscodeBinding(
        VSCodeRenderer.render(macMapping, Platform.MacOS),
        "cursorLeft",
      )
      val linuxKey = findVscodeBinding(
        VSCodeRenderer.render(linuxMapping, Platform.Linux),
        "cursorLeft",
      )
      macKey shouldBe Some("left")
      linuxKey shouldBe Some("left")
    }
  }

  "ZedRenderer" should {
    "render platform-specific bindings for macOS" in {
      val mapping = loadFromYaml(yamlWithPlatformBindings, Platform.MacOS)
      val result  = ZedRenderer.render(mapping, Platform.MacOS)
      result should include("cmd-v")
      result should include("editor::Paste")
    }

    "render platform-specific bindings for Linux (default fallback)" in {
      val mapping = loadFromYaml(yamlWithPlatformBindings, Platform.Linux)
      val result  = ZedRenderer.render(mapping, Platform.Linux)
      result should include("ctrl-v")
      result should include("editor::Paste")
    }
  }

  "IdeaRenderer" should {
    "render platform-specific bindings for macOS" in {
      val mapping = loadFromYaml(yamlWithPlatformBindings, Platform.MacOS)
      val params  = IdeaParams(List.empty, negate = false, parent = "$default", keymapName = "Test")
      val result  = new IdeaRenderer(params).render(mapping, Platform.MacOS)
      result should include("meta v")
      result should include("$Paste")
    }

    "render platform-specific bindings for Linux (default fallback)" in {
      val mapping = loadFromYaml(yamlWithPlatformBindings, Platform.Linux)
      val params  = IdeaParams(List.empty, negate = false, parent = "$default", keymapName = "Test")
      val result  = new IdeaRenderer(params).render(mapping, Platform.Linux)
      result should include("ctrl v")
      result should include("$Paste")
    }
  }

  "Platform-specific binding" should {
    "skip concepts with no binding for the current platform" in {
      val yaml =
        """mapping:
          |  - id: "macOnly"
          |    binding:
          |      macos: "meta+[KeyX]"
          |    vscode:
          |      action: 'macOnlyAction'
          |    idea:
          |      action: 'MacOnlyAction'
          |    zed:
          |      action: "editor::MacOnly"
          |      context: [ "Editor" ]
          |""".stripMargin

      val macMapping = loadFromYaml(yaml, Platform.MacOS)
      macMapping.mapping should have size 1

      val linuxMapping = loadFromYaml(yaml, Platform.Linux)
      linuxMapping.mapping shouldBe empty
    }
  }
}
