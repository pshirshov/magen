package io.septimalmind.magen.model

import io.septimalmind.magen.util.ShortcutParser
import izumi.fundamentals.collections.nonempty.NEList
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MappingValidatorTest extends AnyWordSpec with Matchers {

  private def chord(s: String): Chord            = ShortcutParser.parseUnsafe(s)
  private def chords(ss: String*): NEList[Chord] = NEList.unsafeFrom(ss.map(chord).toList)

  "validateConflicts" should {
    "detect no conflicts when bindings are unique" in {
      val mapping = Mapping(
        List(
          Concept(
            "copy",
            chords("ctrl+[KeyC]"),
            idea   = Some(IdeaAction("$Copy", List.empty)),
            vscode = Some(VSCodeAction("editor.copy", None, List.empty, List.empty)),
            zed    = Some(ZedAction("editor::Copy", None, List("Editor"))),
          ),
          Concept(
            "paste",
            chords("ctrl+[KeyV]"),
            idea   = Some(IdeaAction("$Paste", List.empty)),
            vscode = Some(VSCodeAction("editor.paste", None, List.empty, List.empty)),
            zed    = Some(ZedAction("editor::Paste", None, List("Editor"))),
          ),
        )
      )

      val conflicts = MappingValidator.validateConflicts(mapping)
      conflicts shouldBe empty
    }

    "detect IDEA binding conflict" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyR]"), idea = Some(IdeaAction("RunAction", List.empty)), vscode     = None, zed = None),
          Concept("actionB", chords("ctrl+[KeyR]"), idea = Some(IdeaAction("RefreshAction", List.empty)), vscode = None, zed = None),
        )
      )

      val conflicts = MappingValidator.validateConflicts(mapping)
      conflicts should have size 1
      conflicts.head.editor shouldBe "idea"
      conflicts.head.entries should have size 2
      conflicts.head.entries.map(_.action).toSet shouldBe Set("RunAction", "RefreshAction")
    }

    "detect VSCode binding conflict within same context" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyF]"), idea = None, vscode = Some(VSCodeAction("findAction", None, List("editorFocus"), List.empty)), zed   = None),
          Concept("actionB", chords("ctrl+[KeyF]"), idea = None, vscode = Some(VSCodeAction("formatAction", None, List("editorFocus"), List.empty)), zed = None),
        )
      )

      val conflicts       = MappingValidator.validateConflicts(mapping)
      val vscodeConflicts = conflicts.filter(_.editor == "vscode")
      vscodeConflicts should have size 1
      vscodeConflicts.head.context shouldBe "editorFocus"
    }

    "not flag VSCode bindings in different contexts as conflict" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyF]"), idea = None, vscode = Some(VSCodeAction("findInEditor", None, List("editorFocus"), List.empty)), zed     = None),
          Concept("actionB", chords("ctrl+[KeyF]"), idea = None, vscode = Some(VSCodeAction("findInTerminal", None, List("terminalFocus"), List.empty)), zed = None),
        )
      )

      val conflicts       = MappingValidator.validateConflicts(mapping)
      val vscodeConflicts = conflicts.filter(_.editor == "vscode")
      vscodeConflicts shouldBe empty
    }

    "detect Zed binding conflict within same context" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyR]"), idea = None, vscode = None, zed = Some(ZedAction("editor::Run", None, List("Editor")))),
          Concept("actionB", chords("ctrl+[KeyR]"), idea = None, vscode = None, zed = Some(ZedAction("editor::Refresh", None, List("Editor")))),
        )
      )

      val conflicts    = MappingValidator.validateConflicts(mapping)
      val zedConflicts = conflicts.filter(_.editor == "zed")
      zedConflicts should have size 1
    }

    "detect conflict regardless of modifier order" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+shift+[KeyA]"), idea = Some(IdeaAction("ActionA", List.empty)), vscode = None, zed = None),
          Concept("actionB", chords("shift+ctrl+[KeyA]"), idea = Some(IdeaAction("ActionB", List.empty)), vscode = None, zed = None),
        )
      )

      val conflicts     = MappingValidator.validateConflicts(mapping)
      val ideaConflicts = conflicts.filter(_.editor == "idea")
      ideaConflicts should have size 1
    }

    "not flag same action bound to same key from multiple concepts" in {
      val mapping = Mapping(
        List(
          Concept("copyEditor", chords("ctrl+[KeyC]"), idea   = Some(IdeaAction("$Copy", List.empty)), vscode = None, zed = None),
          Concept("copyTerminal", chords("ctrl+[KeyC]"), idea = Some(IdeaAction("$Copy", List.empty)), vscode = None, zed = None),
        )
      )

      val conflicts     = MappingValidator.validateConflicts(mapping)
      val ideaConflicts = conflicts.filter(_.editor == "idea")
      ideaConflicts shouldBe empty
    }
  }

  "validateCompleteness" should {
    "detect missing platform binding" in {
      val rawConcepts = List(
        RawConcept(
          id = "macOnly",
          binding = PlatformBinding.PerPlatform(
            default = None,
            macos   = Some(List("meta+[KeyX]")),
            linux   = None,
            win     = None,
          ),
          idea   = Some(RawIdeaAction(Some("Action"), None, None)),
          vscode = None,
          zed    = None,
          unset  = None,
        )
      )

      val missing = MappingValidator.validateCompleteness(rawConcepts, Platform.Linux)
      missing should have size 1
      missing.head.conceptId shouldBe "macOnly"
      missing.head.platform shouldBe Platform.Linux
    }

    "not flag concepts with universal bindings" in {
      val rawConcepts = List(
        RawConcept(
          id      = "universal",
          binding = PlatformBinding.Universal(List("ctrl+[KeyA]")),
          idea    = Some(RawIdeaAction(Some("Action"), None, None)),
          vscode  = None,
          zed     = None,
          unset   = None,
        )
      )

      val missing = MappingValidator.validateCompleteness(rawConcepts, Platform.Linux)
      missing shouldBe empty
    }

    "not flag unset concepts" in {
      val rawConcepts = List(
        RawConcept(
          id = "removed",
          binding = PlatformBinding.PerPlatform(
            default = None,
            macos   = Some(List("meta+[KeyX]")),
            linux   = None,
            win     = None,
          ),
          idea   = None,
          vscode = None,
          zed    = None,
          unset  = Some(true),
        )
      )

      val missing = MappingValidator.validateCompleteness(rawConcepts, Platform.Linux)
      missing shouldBe empty
    }

    "not flag concepts with default fallback" in {
      val rawConcepts = List(
        RawConcept(
          id = "withDefault",
          binding = PlatformBinding.PerPlatform(
            default = Some(List("ctrl+[KeyX]")),
            macos   = Some(List("meta+[KeyX]")),
            linux   = None,
            win     = None,
          ),
          idea   = Some(RawIdeaAction(Some("Action"), None, None)),
          vscode = None,
          zed    = None,
          unset  = None,
        )
      )

      val missing = MappingValidator.validateCompleteness(rawConcepts, Platform.Linux)
      missing shouldBe empty
    }

    "not flag concepts with no editor actions" in {
      val rawConcepts = List(
        RawConcept(
          id = "noEditors",
          binding = PlatformBinding.PerPlatform(
            default = None,
            macos   = Some(List("meta+[KeyX]")),
            linux   = None,
            win     = None,
          ),
          idea   = None,
          vscode = None,
          zed    = None,
          unset  = None,
        )
      )

      val missing = MappingValidator.validateCompleteness(rawConcepts, Platform.Linux)
      missing shouldBe empty
    }
  }

  "ValidationResult" should {
    "not treat IDEA conflicts as errors" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyR]"), idea = Some(IdeaAction("RunAction", List.empty)), vscode     = None, zed = None),
          Concept("actionB", chords("ctrl+[KeyR]"), idea = Some(IdeaAction("RefreshAction", List.empty)), vscode = None, zed = None),
        )
      )

      val result = MappingValidator.validate(mapping, List.empty, Platform.Linux)
      result.ideaConflicts should have size 1
      result.errors shouldBe empty
      result.hasErrors shouldBe false
    }

    "treat VSCode conflicts as errors" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyW]"), idea = None, zed = None, vscode = Some(VSCodeAction("closeWindow", None, List.empty, List.empty))),
          Concept("actionB", chords("ctrl+[KeyW]"), idea = None, zed = None, vscode = Some(VSCodeAction("toggleWrap", None, List.empty, List.empty))),
        )
      )

      val result = MappingValidator.validate(mapping, List.empty, Platform.Linux)
      result.errors should have size 1
      result.hasErrors shouldBe true
    }

    "treat Zed conflicts as errors" in {
      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyR]"), idea = None, vscode = None, zed = Some(ZedAction("editor::Run", None, List("Editor")))),
          Concept("actionB", chords("ctrl+[KeyR]"), idea = None, vscode = None, zed = Some(ZedAction("editor::Refresh", None, List("Editor")))),
        )
      )

      val result = MappingValidator.validate(mapping, List.empty, Platform.Linux)
      result.errors should have size 1
      result.hasErrors shouldBe true
    }
  }

  "validate (integration)" should {
    "collect IDEA warnings and missing bindings" in {
      val rawConcepts = List(
        RawConcept(
          id      = "actionA",
          binding = PlatformBinding.Universal(List("ctrl+[KeyR]")),
          idea    = Some(RawIdeaAction(Some("RunAction"), None, None)),
          vscode  = None,
          zed     = None,
          unset   = None,
        ),
        RawConcept(
          id      = "actionB",
          binding = PlatformBinding.Universal(List("ctrl+[KeyR]")),
          idea    = Some(RawIdeaAction(Some("RefreshAction"), None, None)),
          vscode  = None,
          zed     = None,
          unset   = None,
        ),
        RawConcept(
          id = "macOnly",
          binding = PlatformBinding.PerPlatform(
            default = None,
            macos   = Some(List("meta+[KeyX]")),
            linux   = None,
            win     = None,
          ),
          idea   = Some(RawIdeaAction(Some("MacAction"), None, None)),
          vscode = None,
          zed    = None,
          unset  = None,
        ),
      )

      val mapping = Mapping(
        List(
          Concept("actionA", chords("ctrl+[KeyR]"), idea = Some(IdeaAction("RunAction", List.empty)), vscode     = None, zed = None),
          Concept("actionB", chords("ctrl+[KeyR]"), idea = Some(IdeaAction("RefreshAction", List.empty)), vscode = None, zed = None),
        )
      )

      val result = MappingValidator.validate(mapping, rawConcepts, Platform.Linux)
      result.ideaConflicts should have size 1
      result.errors shouldBe empty
      result.hasErrors shouldBe false
      result.missingBindings should have size 1
    }
  }
}
