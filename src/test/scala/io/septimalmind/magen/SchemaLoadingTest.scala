package io.septimalmind.magen

import io.septimalmind.magen.model.Platform
import io.septimalmind.magen.util.{MagenPaths, MappingsSource, NegationsSource}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class SchemaLoadingTest extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    MagenPaths.configure(MappingsSource.Classpath)
    MagenPaths.configureNegations(NegationsSource.Classpath)
  }

  "Schema loading from classpath" should {
    "list all bundled schemes" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val schemes = Magen.listSchemes()
      schemes.map(_.name) should contain allOf ("pshirshov", "idea-macos", "from-idea")
    }

    "load pshirshov scheme for macOS" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val mapping = Magen.loadScheme(model.SchemeId("pshirshov"), Platform.MacOS)
      mapping.mapping should not be empty
    }

    "load idea-macos scheme for macOS" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val mapping = Magen.loadScheme(model.SchemeId("idea-macos"), Platform.MacOS)
      mapping.mapping should not be empty
    }

    "load from-idea scheme for macOS" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val mapping = Magen.loadScheme(model.SchemeId("from-idea"), Platform.MacOS)
      mapping.mapping should not be empty
    }

    "load and validate pshirshov scheme for all platforms" in {
      MagenPaths.configure(MappingsSource.Classpath)
      for (platform <- List(Platform.MacOS, Platform.Linux, Platform.Win)) {
        val (mapping, validation) = Magen.loadAndValidate(model.SchemeId("pshirshov"), platform)
        mapping.mapping should not be empty
        validation.errors shouldBe empty
      }
    }

    "load and validate idea-macos scheme for all platforms" in {
      MagenPaths.configure(MappingsSource.Classpath)
      for (platform <- List(Platform.MacOS, Platform.Linux, Platform.Win)) {
        val (mapping, validation) = Magen.loadAndValidate(model.SchemeId("idea-macos"), platform)
        mapping.mapping should not be empty
        validation.errors shouldBe empty
      }
    }

    "fail on nonexistent scheme" in {
      MagenPaths.configure(MappingsSource.Classpath)
      an[AssertionError] should be thrownBy {
        Magen.loadScheme(model.SchemeId("nonexistent"), Platform.MacOS)
      }
    }

    "render pshirshov scheme to all formats" in {
      MagenPaths.configure(MappingsSource.Classpath)
      val mapping = Magen.loadScheme(model.SchemeId("pshirshov"), Platform.MacOS)

      val vscode = targets.VSCodeRenderer.render(mapping, Platform.MacOS)
      vscode should include("key")
      vscode should include("command")

      val ideaRenderer = new targets.IdeaRenderer(
        targets.IdeaParams(List.empty, negate = true, parent = "$default", keymapName = "Test"),
      )
      val idea = ideaRenderer.render(mapping, Platform.MacOS)
      idea should include("<keymap")
      idea should include("keyboard-shortcut")

      val zed = targets.ZedRenderer.render(mapping, Platform.MacOS)
      zed should include("bindings")
      zed should include("context")
    }
  }
}
