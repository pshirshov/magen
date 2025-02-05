package io.septimalmind.magen

import io.septimalmind.magen.Key.{KeyCombo, NamedKey}

import scala.annotation.tailrec
import scala.xml.PrettyPrinter

object IdeaRenderer extends Renderer {
  // useful: https://github.com/JetBrains/intellij-community/blob/ed982b658a0688970a7773fbb81fce3723ab5416/plugins/ide-startup/importSettings/src/com/intellij/ide/startup/importSettings/transfer/backend/providers/vscode/mappings/KeyBindingsMappings.kt#L115
  override def id: String = "idea.xml"

  override def render(mapping: Mapping): String = {
    val mappings = for {
      c <- mapping.mapping
      a <- c.idea.toList

    } yield {
      val bbs = c.binding.map(ShortcutParser.parseUnsafe).flatMap(Aliases.extend).map(b => format(b))
      <action id={a.action}>
        {bbs}
      </action>
    }

    val full = <keymap version="1" name="Magen" parent="Empty">
      {mappings}
    </keymap>
    val pp = new PrettyPrinter(120, 2)
    val prettyXml: String = pp.format(full)
    prettyXml
  }

  private def format(binding: Chord) = {
    binding.combos match {
      case f :: s :: Nil =>
        val fs = renderCombo(f)
        val ss = renderCombo(s)
        Seq(<keyboard-shortcut first-keystroke={fs} second-keystroke={ss} />)
      case f :: Nil =>
        val fs = renderCombo(f)
        Seq(<keyboard-shortcut first-keystroke={fs} />)
      case o =>
        println(o)
        ???
    }
  }

  private def renderCombo(f: KeyCombo): String = {
    val modsStr = f.modifiers.map {
      case Modifier.Ctrl => "ctrl"
      case Modifier.Alt => "alt"
      case Modifier.Shift => "shift"
      case Modifier.Meta => "meta"
    }

    (modsStr :+ renderKey(f.key)).mkString(" ").toLowerCase
  }

  private def renderKey(f: NamedKey): String = {
    shortcutMap(f.name)
  }

  @tailrec
  def shortcutMap(shortcut: String): String = {
    if (shortcut.startsWith("[") && shortcut.endsWith("]")) {
      shortcutMap(shortcut.substring(1, shortcut.length - 1))
    } else {
      shortcut.toUpperCase match {
        case "SHIFT" => "shift"
        case "ALT" => "alt"
        case "CMD" => "meta"
        case "CTRL" => "ctrl"
        case "-" => "MINUS"
        case "=" => "EQUALS"
        case "BACKSPACE" => "BACK_SPACE"
        case "," => "COMMA"
        case ";" => "SEMICOLON"
        case "." => "PERIOD"
        case "/" => "SLASH"
        case "\\" => "BACK_SLASH"
        case "PAGEDOWN" => "PAGE_DOWN"
        case "PAGEUP" => "PAGE_UP"
        case "[" => "OPEN_BRACKET"
        case "]" => "CLOSE_BRACKET"
        case "'" => "AMPERSAND"
        case _ => shortcut
      }
    }
  }

  val basicMappings = Map(
    "actions.find" -> "Find",
    "cursorBottom" -> "EditorTextEnd",
    "cursorEnd" -> "EditorLineEnd",
    "cursorHome" -> "EditorLineStart",
    "cursorTop" -> "EditorTextStart",
    "editor.action.addCommentLine" -> "CommentByLineComment",
    "editor.action.addSelectionToNextFindMatch" -> "SelectNextOccurrence",
    "editor.action.blockComment" -> "CommentByBlockComment",
    "editor.action.changeAll" -> "SelectAllOccurrences",
    "editor.action.clipboardCopyAction" -> "\\$Copy",
    "editor.action.clipboardCutAction" -> "\\$Cut",
    "editor.action.clipboardPasteAction" -> "\\$Paste",
    "editor.action.commentLine" -> "CommentByLineComment",
    "editor.action.copyLinesDownAction" -> "EditorDuplicate",
    "editor.action.deleteLines" -> "EditorDeleteLine",
    "editor.action.formatDocument" -> "ReformatCode",
    "editor.action.goToReferences" -> "ShowUsages",
    "editor.action.indentLines" -> "EditorIndentLineOrSelection",
    "editor.action.insertCursorAbove" -> "EditorCloneCaretAbove",
    "editor.action.insertCursorAtEndOfEachLineSelected" -> "EditorToggleColumnMode",
    "editor.action.insertCursorBelow" -> "EditorCloneCaretBelow",
    "editor.action.insertLineAfter" -> "EditorStartNewLine",
    "editor.action.insertLineBefore" -> "EditorStartNewLineBefore",
    "editor.action.jumpToBracket" -> "EditorMatchBrace",
    "editor.action.marker.nextInFiles" -> "GotoNextError",
    "editor.action.marker.prevInFiles" -> "GotoPreviousError",
    "editor.action.moveLinesDownAction" -> "MoveLineDown",
    "editor.action.moveLinesUpAction" -> "MoveLineUp",
    "editor.action.outdentLines" -> "EditorUnindentSelection",
    "editor.action.peekDefinition" -> "QuickImplementations",
    "editor.action.quickFix" -> "ShowIntentionActions",
    "editor.action.removeCommentLine" -> "CommentByLineComment",
    "editor.action.rename" -> "RenameElement",
    "editor.action.revealDefinition" -> "GotoDeclaration",
    "editor.action.selectHighlights" -> "SelectAllOccurrences",
    "editor.action.startFindReplaceAction" -> "Replace",
    "editor.action.triggerParameterHints" -> "ParameterInfo",
    "editor.debug.action.toggleBreakpoint" -> "ToggleLineBreakpoint",
    "editor.fold" -> "CollapseRegion",
    "editor.foldAll" -> "CollapseAllRegions",
    "editor.foldRecursively" -> "CollapseRegionRecursively",
    "editor.unfold" -> "ExpandRegion",
    "editor.unfoldAll" -> "ExpandAllRegions",
    "editor.unfoldRecursively" -> "ExpandRegionRecursively",
    "markdown.showPreview" -> "org.intellij.plugins.markdown.ui.actions.editorLayout.PreviewOnlyLayoutChangeAction",
    "markdown.showPreviewToSide" -> "org.intellij.plugins.markdown.ui.actions.editorLayout.EditorAndPreviewLayoutChangeAction",
    "redo" -> "\\$Redo",
    "scrollLineDown" -> "EditorScrollDown",
    "scrollLineUp" -> "EditorScrollUp",
    "scrollPageDown" -> "ScrollPane-scrollDown",
    "scrollPageUp" -> "ScrollPane-scrollUp",
    "undo" -> "\\$Undo",
    "workbench.action.closeActiveEditor" -> "CloseContent",
    "workbench.action.closeAllEditors" -> "CloseAllEditors",
    "workbench.action.closeFolder" -> "CloseProject",
    "workbench.action.debug.run" -> "Run",
    "workbench.action.debug.start" -> "Debug",
    "workbench.action.debug.stepInto" -> "StepInto",
    "workbench.action.debug.stepOut" -> "StepOut",
    "workbench.action.debug.stepOver" -> "StepOver",
    "workbench.action.files.newUntitledFile" -> "FileChooser.NewFile",
    "workbench.action.files.openFileFolder" -> "OpenFile",
    "workbench.action.files.revealActiveFileInWindows" -> "RevealIn",
    "workbench.action.files.save" -> "SaveAll",
    "workbench.action.files.saveAll" -> "SaveAll",
    "workbench.action.files.showOpenedFileInNewWindow" -> "EditSourceInNewWindow",
    "workbench.action.gotoLine" -> "GotoLine",
    "workbench.action.navigateBack" -> "Back",
    "workbench.action.navigateForward" -> "Forward",
    "workbench.action.openSettings" -> "ShowSettings",
    "workbench.action.output.toggleOutput" -> "ActivateRunToolWindow",
    "workbench.action.quickOpen" -> "GotoFile",
    "workbench.action.reopenClosedEditor" -> "ReopenClosedTab",
    "workbench.action.replaceInFiles" -> "ReplaceInPath",
    "workbench.action.selectTheme" -> "QuickChangeScheme",
    "workbench.action.showAllSymbols" -> "GotoSymbol",
    "workbench.action.splitEditor" -> "SplitVertically",
    "workbench.action.tasks.build" -> "CompileDirty",
    "workbench.action.terminal.toggleTerminal" -> "ActivateTerminalToolWindow",
    "workbench.action.toggleSidebarVisibility" -> "HideSideWindows",
    "workbench.action.toggleZenMode" -> "ToggleDistractionFreeMode",
    "workbench.action.zoomIn" -> "EditorIncreaseFontSize",
    "workbench.action.zoomOut" -> "EditorDecreaseFontSize",
    "workbench.action.zoomReset" -> "EditorResetFontSize",
    "workbench.view.debug" -> "ActivateDebugToolWindow",
    "workbench.view.explorer" -> "ActivateProjectToolWindow",
    "workbench.view.extensions" -> "WelcomeScreen.Plugins",
    "workbench.view.scm" -> "ActivateVersionControlToolWindow",
  )
}
