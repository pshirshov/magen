# Zed Editor Action Mapping

This document maps actions from our keymap model to Zed editor actions.

## Cursor Movement

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| cursorLeft | cursorLeft | editor::MoveLeft |
| cursorRight | cursorRight | editor::MoveRight |
| cursorUp | cursorUp | editor::MoveUp |
| cursorDown | cursorDown | editor::MoveDown |
| cursorWordLeft | cursorWordLeft | editor::MoveToPreviousWordStart |
| cursorWordEndRight | cursorWordEndRight | editor::MoveToNextWordEnd |
| cursorHome | cursorHome | editor::MoveToBeginningOfLine |
| cursorEnd | cursorEnd | editor::MoveToEndOfLine |
| cursorPageDown | cursorPageDown | editor::PageDown |
| cursorPageUp | cursorPageUp | editor::PageUp |
| cursorTop | cursorTop | editor::MoveToBeginning |
| cursorBottom | cursorBottom | editor::MoveToEnd |

## Selection

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| select-all | editor.action.selectAll | editor::SelectAll |
| cursorRightSelect | cursorRightSelect | editor::SelectRight |
| cursorLeftSelect | cursorLeftSelect | editor::SelectLeft |
| cursorUpSelect | cursorUpSelect | editor::SelectUp |
| cursorDownSelect | cursorDownSelect | editor::SelectDown |
| cursorWordLeftSelect | cursorWordLeftSelect | editor::SelectToPreviousWordStart |
| cursorWordEndRightSelect | cursorWordEndRightSelect | editor::SelectToNextWordEnd |
| cursorHomeSelect | cursorHomeSelect | editor::SelectToBeginningOfLine |
| cursorEndSelect | cursorEndSelect | editor::SelectToEndOfLine |
| cursorPageDownSelect | cursorPageDownSelect | editor::SelectPageDown |
| cursorPageUpSelect | cursorPageUpSelect | editor::SelectPageUp |
| cursorTopSelect | cursorTopSelect | editor::SelectToBeginning |
| cursorBottomSelect | cursorBottomSelect | editor::SelectToEnd |

## Multi-Cursor

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| editor.action.insertCursorAbove | editor.action.insertCursorAbove | editor::AddSelectionAbove |
| editor.action.insertCursorBelow | editor.action.insertCursorBelow | editor::AddSelectionBelow |
| removeSecondaryCursors | removeSecondaryCursors | editor::Cancel (when multiple selections) |

## Edit Operations

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| undo | undo | editor::Undo |
| redo | redo | editor::Redo |
| deleteLeft | deleteLeft | editor::Backspace |
| deleteRight | deleteRight | editor::Delete |
| deleteWordLeft | deleteWordLeft | editor::DeleteToPreviousWordStart |
| editor.action.deleteLines | editor.action.deleteLines | editor::DeleteLine |
| editor.action.duplicateSelection | editor.action.duplicateSelection | editor::DuplicateLineDown |
| editor.action.insertLineAfter | editor.action.insertLineAfter | editor::NewlineBelow |
| editor.action.insertLineBefore | editor.action.insertLineBefore | editor::NewlineAbove |
| editor.action.moveLinesDownAction | editor.action.moveLinesDownAction | editor::MoveLineDown |
| editor.action.moveLinesUpAction | editor.action.moveLinesUpAction | editor::MoveLineUp |
| editor.action.commentLine | editor.action.commentLine | editor::ToggleComments |
| editor.action.blockComment | editor.action.blockComment | editor::ToggleComments |
| editor.action.formatDocument | editor.action.formatDocument | editor::Format |
| editor.action.indentLines | editor.action.indentLines | editor::Indent |
| editor.action.outdentLines | editor.action.outdentLines | editor::Outdent |

## Navigation

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| editor.action.jumpToBracket | editor.action.jumpToBracket | editor::MoveToEnclosingBracket |
| workbench.action.gotoLine | workbench.action.gotoLine | go_to_line::Toggle |
| workbench.action.navigateForward | workbench.action.navigateForward | pane::GoForward |
| workbench.action.navigateBack | workbench.action.navigateBack | pane::GoBack |
| workbench.action.nextEditor | workbench.action.nextEditor | pane::ActivateNextItem |
| workbench.action.previousEditor | workbench.action.previousEditor | pane::ActivatePreviousItem |
| workbench.action.quickOpen | workbench.action.quickOpen | file_finder::Toggle |
| workbench.action.openRecent | workbench.action.openRecent | projects::OpenRecent |
| workbench.action.showAllSymbols | workbench.action.showAllSymbols | project_symbols::Toggle |
| workbench.action.showCommands | workbench.action.showCommands | command_palette::Toggle |
| workbench.action.gotoSymbol | workbench.action.gotoSymbol | outline::Toggle |
| editor.action.goToDeclaration | editor.action.goToDeclaration | editor::GoToDefinition |
| editor.action.revealDefinition | editor.action.revealDefinition | editor::GoToDefinition |
| editor.action.goToTypeDefinition | editor.action.goToTypeDefinition | editor::GoToTypeDefinition |
| editor.action.goToImplementation | editor.action.goToImplementation | editor::GoToImplementation |
| editor.action.goToReferences | editor.action.goToReferences | editor::FindAllReferences |
| editor.action.peekDefinition | editor.action.peekDefinition | editor::GoToDefinition |
| editor.action.revealDefinitionAside | editor.action.revealDefinitionAside | editor::GoToDefinitionSplit |
| editor.action.marker.next | editor.action.marker.next | editor::GoToDiagnostic |
| editor.action.marker.prev | editor.action.marker.prev | editor::GoToPreviousDiagnostic |

## Commands (File/Window Operations)

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| workbench.action.closeActiveEditor | workbench.action.closeActiveEditor | pane::CloseActiveItem |
| workbench.action.closeWindow | workbench.action.closeWindow | workspace::CloseWindow |
| workbench.action.files.save | workbench.action.files.save | workspace::Save |
| workbench.action.files.saveAll | workbench.action.files.saveAll | workspace::SaveAll |
| workbench.action.quit | workbench.action.quit | zed::Quit |
| workbench.action.reopenClosedEditor | workbench.action.reopenClosedEditor | pane::ReopenClosedItem |
| workbench.action.closeOtherEditors | workbench.action.closeOtherEditors | pane::CloseOtherItems |
| workbench.action.newWindow | workbench.action.newWindow | workspace::NewWindow |
| workbench.action.files.newUntitledFile | workbench.action.files.newUntitledFile | workspace::NewFile |
| workbench.action.splitEditorRight | workbench.action.splitEditorRight | pane::SplitRight |

## UI

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| workbench.action.openSettings | workbench.action.openSettings | zed::OpenSettings |
| workbench.action.zoomIn | workbench.action.zoomIn | zed::IncreaseBufferFontSize |
| workbench.action.zoomOut | workbench.action.zoomOut | zed::DecreaseBufferFontSize |
| workbench.action.zoomReset | workbench.action.zoomReset | zed::ResetBufferFontSize |
| workbench.action.terminal.toggleTerminal | workbench.action.terminal.toggleTerminal | workspace::NewTerminal |
| workbench.action.togglePanel | workbench.action.togglePanel | workspace::ToggleBottomDock |
| workbench.action.toggleSidebarVisibility | workbench.action.toggleSidebarVisibility | workspace::ToggleLeftDock |

## Search

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| actions.find | actions.find | buffer_search::Deploy |
| editor.action.startFindReplaceAction | editor.action.startFindReplaceAction | buffer_search::DeployReplace |
| workbench.action.findInFiles | workbench.action.findInFiles | project_search::Deploy |
| workbench.action.replaceInFiles | workbench.action.replaceInFiles | project_search::Deploy (with replace) |
| editor.action.nextMatchFindAction | editor.action.nextMatchFindAction | search::SelectNextMatch |
| editor.action.previousMatchFindAction | editor.action.previousMatchFindAction | search::SelectPreviousMatch |
| editor.action.selectAllMatches | editor.action.selectAllMatches | search::SelectAllMatches |
| toggleFindCaseSensitive | toggleFindCaseSensitive | search::ToggleCaseSensitive |
| toggleFindWholeWord | toggleFindWholeWord | search::ToggleWholeWord |
| toggleFindRegex | toggleFindRegex | search::ToggleRegex |

## Intellisense

| Our ID | VSCode Action | Zed Action |
|--------|--------------|-----------|
| editor.action.triggerSuggest | editor.action.triggerSuggest | editor::ShowCompletions |
| editor.action.triggerParameterHints | editor.action.triggerParameterHints | editor::ShowSignatureHelp |
| editor.action.quickFix | editor.action.quickFix | editor::ToggleCodeActions |
| editor.action.autoFix | editor.action.autoFix | editor::ToggleCodeActions |

## Context-Specific Notes

- Zed contexts are simpler than VSCode's. Main contexts are:
  - `Editor` - when in text editor
  - `Terminal` - when in terminal
  - `ProjectPanel` - when in project panel
  - No context means always active

- Unlike IntelliJ, Zed doesn't need negation. Unmapped keys remain unbound.
