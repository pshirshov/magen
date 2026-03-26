package io.septimalmind.magen.cli

import io.septimalmind.magen.model.{Platform, SchemeId}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Paths

class CliParserTest extends AnyWordSpec with Matchers {

  "CliParser.parse" should {
    "parse command as first positional arg" in {
      val (cmd, parsed) = CliParser.parse(List("generate"))
      cmd shouldBe Some("generate")
      parsed.positional shouldBe empty
    }

    "parse command with flags after it" in {
      val (cmd, parsed) = CliParser.parse(List("generate", "--scheme", "test", "--platform", "macos"))
      cmd shouldBe Some("generate")
      parsed.scheme shouldBe Some(SchemeId("test"))
      parsed.platform shouldBe Some(Platform.MacOS)
    }

    "parse --mappings" in {
      val (cmd, parsed) = CliParser.parse(List("import", "vscode", "--mappings", "/tmp/m", "--scheme", "s"))
      cmd shouldBe Some("import")
      parsed.mappingsDir shouldBe Some(Paths.get("/tmp/m"))
      parsed.scheme shouldBe Some(SchemeId("s"))
      parsed.positional shouldBe List("vscode")
    }

    "parse --negations" in {
      val (cmd, parsed) = CliParser.parse(List("import-negation", "idea", "--negations", "/tmp/n"))
      cmd shouldBe Some("import-negation")
      parsed.negationsDir shouldBe Some(Paths.get("/tmp/n"))
      parsed.positional shouldBe List("idea")
    }

    "parse --keymap" in {
      val (cmd, parsed) = CliParser.parse(List("import", "vscode", "--keymap", "/path/to/file.json", "--scheme", "s", "--mappings", "/m"))
      cmd shouldBe Some("import")
      parsed.keymap shouldBe Some(Paths.get("/path/to/file.json"))
      parsed.positional shouldBe List("vscode")
    }

    "parse --keymap-id" in {
      val (cmd, parsed) = CliParser.parse(List("import", "idea", "--keymap-id", "$default", "--scheme", "s", "--mappings", "/m"))
      cmd shouldBe Some("import")
      parsed.keymapId shouldBe Some("$default")
    }

    "parse all flags together" in {
      val (cmd, parsed) = CliParser.parse(List(
        "import", "idea",
        "--mappings", "/m",
        "--negations", "/n",
        "--scheme", "test",
        "--platform", "linux",
        "--keymap", "/k.xml",
        "--keymap-id", "Vim",
      ))
      cmd shouldBe Some("import")
      parsed.mappingsDir shouldBe Some(Paths.get("/m"))
      parsed.negationsDir shouldBe Some(Paths.get("/n"))
      parsed.scheme shouldBe Some(SchemeId("test"))
      parsed.platform shouldBe Some(Platform.Linux)
      parsed.keymap shouldBe Some(Paths.get("/k.xml"))
      parsed.keymapId shouldBe Some("Vim")
      parsed.positional shouldBe List("idea")
    }

    "return None command for empty args" in {
      val (cmd, parsed) = CliParser.parse(List.empty)
      cmd shouldBe None
      parsed.mappingsDir shouldBe None
    }

    "collect subcommand as positional" in {
      val (cmd, parsed) = CliParser.parse(List("import", "vscode", "--scheme", "s", "--mappings", "/m"))
      cmd shouldBe Some("import")
      parsed.positional shouldBe List("vscode")
    }

    "collect render dir as positional" in {
      val (cmd, parsed) = CliParser.parse(List("render", "/tmp/output", "--scheme", "s"))
      cmd shouldBe Some("render")
      parsed.positional shouldBe List("/tmp/output")
    }

    "throw on unknown flag" in {
      an[IllegalArgumentException] should be thrownBy {
        CliParser.parse(List("generate", "--unknown", "value"))
      }
    }

    "throw when --mappings has no value" in {
      an[AssertionError] should be thrownBy {
        CliParser.parse(List("generate", "--mappings"))
      }
    }

    "throw when --scheme has no value" in {
      an[AssertionError] should be thrownBy {
        CliParser.parse(List("generate", "--scheme"))
      }
    }

    "throw when --platform has no value" in {
      an[AssertionError] should be thrownBy {
        CliParser.parse(List("generate", "--platform"))
      }
    }

    "throw when --keymap has no value" in {
      an[AssertionError] should be thrownBy {
        CliParser.parse(List("import", "vscode", "--keymap"))
      }
    }

    "throw when --keymap-id has no value" in {
      an[AssertionError] should be thrownBy {
        CliParser.parse(List("import", "idea", "--keymap-id"))
      }
    }

    "throw when --negations has no value" in {
      an[AssertionError] should be thrownBy {
        CliParser.parse(List("import-negation", "idea", "--negations"))
      }
    }

    "parse windows platform" in {
      val (_, parsed) = CliParser.parse(List("generate", "--platform", "win"))
      parsed.platform shouldBe Some(Platform.Win)
    }

    "throw on unknown platform value" in {
      an[IllegalArgumentException] should be thrownBy {
        CliParser.parse(List("generate", "--platform", "beos"))
      }
    }
  }
}
