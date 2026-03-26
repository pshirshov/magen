package io.septimalmind.magen.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PlatformTest extends AnyWordSpec with Matchers {

  "Platform.parse" should {
    "parse macos variants" in {
      Platform.parse("macos") shouldBe Platform.MacOS
      Platform.parse("mac") shouldBe Platform.MacOS
      Platform.parse("MacOS") shouldBe Platform.MacOS
    }

    "parse linux" in {
      Platform.parse("linux") shouldBe Platform.Linux
      Platform.parse("Linux") shouldBe Platform.Linux
    }

    "parse windows variants" in {
      Platform.parse("win") shouldBe Platform.Win
      Platform.parse("windows") shouldBe Platform.Win
      Platform.parse("Win") shouldBe Platform.Win
    }

    "throw on unknown platform" in {
      an[IllegalArgumentException] should be thrownBy Platform.parse("bsd")
    }
  }
}
