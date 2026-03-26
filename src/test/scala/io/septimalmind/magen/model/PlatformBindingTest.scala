package io.septimalmind.magen.model

import io.circe.yaml
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PlatformBindingTest extends AnyWordSpec with Matchers {

  private def decode(yamlStr: String): PlatformBinding = {
    val json = yaml.v12.parser.parse(yamlStr).toOption.get
    json.as[PlatformBinding].toOption.get
  }

  "PlatformBinding decoder" should {
    "decode a single string as Universal" in {
      val result = decode("\"alt+left\"")
      result shouldBe PlatformBinding.Universal(List("alt+left"))
    }

    "decode a list of strings as Universal" in {
      val result = decode(
        """- "ctrl+left"
          |- "alt+left"
          |""".stripMargin
      )
      result shouldBe PlatformBinding.Universal(List("ctrl+left", "alt+left"))
    }

    "decode platform object with string values as PerPlatform" in {
      val result = decode(
        """macos: "meta+v"
          |default: "ctrl+v"
          |""".stripMargin
      )
      result shouldBe PlatformBinding.PerPlatform(
        default = Some(List("ctrl+v")),
        macos = Some(List("meta+v")),
        linux = None,
        win = None,
      )
    }

    "decode platform object with list values as PerPlatform" in {
      val result = decode(
        """macos:
          |  - "meta+v"
          |  - "meta+shift+v"
          |linux:
          |  - "ctrl+v"
          |default:
          |  - "ctrl+v"
          |""".stripMargin
      )
      result shouldBe PlatformBinding.PerPlatform(
        default = Some(List("ctrl+v")),
        macos = Some(List("meta+v", "meta+shift+v")),
        linux = Some(List("ctrl+v")),
        win = None,
      )
    }

    "decode platform object with all platforms" in {
      val result = decode(
        """macos: "meta+c"
          |linux: "ctrl+c"
          |win: "ctrl+c"
          |default: "ctrl+c"
          |""".stripMargin
      )
      result shouldBe PlatformBinding.PerPlatform(
        default = Some(List("ctrl+c")),
        macos = Some(List("meta+c")),
        linux = Some(List("ctrl+c")),
        win = Some(List("ctrl+c")),
      )
    }
  }

  "PlatformBinding.resolve" should {
    "resolve Universal to the same bindings for all platforms" in {
      val binding = PlatformBinding.Universal(List("ctrl+v"))
      binding.resolve(Platform.MacOS) shouldBe List("ctrl+v")
      binding.resolve(Platform.Linux) shouldBe List("ctrl+v")
      binding.resolve(Platform.Win) shouldBe List("ctrl+v")
    }

    "resolve PerPlatform to platform-specific binding" in {
      val binding = PlatformBinding.PerPlatform(
        default = Some(List("ctrl+v")),
        macos = Some(List("meta+v")),
        linux = None,
        win = Some(List("ctrl+v")),
      )
      binding.resolve(Platform.MacOS) shouldBe List("meta+v")
      binding.resolve(Platform.Win) shouldBe List("ctrl+v")
    }

    "fall back to default when platform not specified" in {
      val binding = PlatformBinding.PerPlatform(
        default = Some(List("ctrl+v")),
        macos = Some(List("meta+v")),
        linux = None,
        win = None,
      )
      binding.resolve(Platform.Linux) shouldBe List("ctrl+v")
      binding.resolve(Platform.Win) shouldBe List("ctrl+v")
    }

    "return empty list when no match and no default" in {
      val binding = PlatformBinding.PerPlatform(
        default = None,
        macos = Some(List("meta+v")),
        linux = None,
        win = None,
      )
      binding.resolve(Platform.Linux) shouldBe List.empty
      binding.resolve(Platform.Win) shouldBe List.empty
    }
  }
}
