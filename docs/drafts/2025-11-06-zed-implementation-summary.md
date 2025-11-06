# Zed Editor Keymap Implementation Summary

## Overview
This document summarizes the complete implementation of Zed editor keymap generation support for the Magen project.

## Changes Made

### 1. Path Expander with Wildcard Support

**File**: `src/main/scala/io/septimalmind/magen/util/PathExpander.scala`

- Created reusable `PathExpander` object with glob pattern support
- Supports `*` and `?` wildcards in path segments
- Supports `~` home directory alias expansion
- Methods:
  - `expandGlob(pattern: String): List[Path]` - Expands a single pattern
  - `expandGlobs(patterns: List[String]): List[Path]` - Expands multiple patterns

**Example Usage**:
```scala
PathExpander.expandGlobs(List(
  "~/.config/JetBrains/*/keymaps/Magen.xml",
  "~/.config/zed/keymap.json"
))
```

### 2. Updated Installer Infrastructure

**File**: `src/main/scala/io/septimalmind/magen/targets/Installer.scala`

- Changed all installer params to accept `List[String]` patterns instead of `List[Path]`
- Embedded `PathExpander.expandGlobs()` calls directly in installers
- Updated:
  - `IdeaParams.writeToPatterns: List[String]`
  - `VscodeParams.writeToPatterns: List[String]`
  - `ZedParams.writeToPatterns: List[String]`

### 3. Zed Renderer Implementation

**File**: `src/main/scala/io/septimalmind/magen/targets/ZedRenderer.scala`

Complete renderer for Zed keymap format with:
- JSON output grouped by context
- Key chord rendering (e.g., `ctrl-shift-up`)
- Action format support
- Context-aware bindings

**Key Format Differences**:
- Modifiers: `ctrl`, `alt`, `shift`, `cmd` (not `meta`)
- Separator: `-` (not `+`)
- Special keys: lowercase (e.g., `escape`, `enter`, `tab`)
- Arrow keys: `left`, `right`, `up`, `down`

**Output Structure**:
```json
[
  {
    "bindings": {
      "left": "editor::MoveLeft",
      "right": "editor::MoveRight"
    }
  },
  {
    "context": "Editor",
    "bindings": {
      "ctrl-shift-up": "editor::AddSelectionAbove"
    }
  }
]
```

### 4. YAML Mapping Updates

Added Zed mappings to all core mapping files:

#### cursor.yaml (11 mappings)
- Basic movement: `MoveLeft`, `MoveRight`, `MoveUp`, `MoveDown`
- Word movement: `MoveToPreviousWordStart`, `MoveToNextWordEnd`
- Line movement: `MoveToBeginningOfLine`, `MoveToEndOfLine`
- Page/document: `PageUp`, `PageDown`, `MoveToBeginning`, `MoveToEnd`
- Multi-cursor: `AddSelectionAbove`, `AddSelectionBelow`, `Cancel`

#### selection.yaml (11 mappings)
- All selection variants: `SelectLeft`, `SelectRight`, `SelectUp`, `SelectDown`
- Word selection: `SelectToPreviousWordStart`, `SelectToNextWordEnd`
- Line selection: `SelectToBeginningOfLine`, `SelectToEndOfLine`
- Page selection: `SelectPageUp`, `SelectPageDown`
- Document selection: `SelectToBeginning`, `SelectToEnd`, `SelectAll`

#### edit.yaml (17 mappings)
- Undo/Redo: `Undo`, `Redo`
- Delete: `Backspace`, `Delete`, `DeleteToPreviousWordStart`, `DeleteLine`
- Line operations: `DuplicateLineDown`, `NewlineBelow`, `NewlineAbove`
- Move lines: `MoveLineUp`, `MoveLineDown`
- Comments: `ToggleComments` (works for both line and block)
- Formatting: `Format`, `Indent`, `Outdent`

#### navigation.yaml (18 mappings)
- Bracket navigation: `MoveToEnclosingBracket`
- Line navigation: `go_to_line::Toggle`
- History: `GoForward`, `GoBack`
- Tab navigation: `ActivateNextItem`, `ActivatePreviousItem`
- File navigation: `file_finder::Toggle`, `OpenRecent`
- Symbol navigation: `project_symbols::Toggle`, `outline::Toggle`
- Code navigation: `GoToDefinition`, `GoToTypeDefinition`, `GoToImplementation`
- References: `FindAllReferences`
- Diagnostics: `GoToDiagnostic`, `GoToPreviousDiagnostic`
- Command palette: `command_palette::Toggle`

#### commands.yaml (10 mappings)
- File operations: `Save`, `SaveAll`
- Window/editor: `CloseActiveItem`, `CloseWindow`, `CloseOtherItems`
- Tab operations: `ReopenClosedItem`
- Window management: `NewWindow`, `NewFile`
- Split: `SplitRight`
- Quit: `Quit`

#### search.yaml (11 mappings)
- Find/replace: `Deploy` (buffer_search and project_search)
- Navigation: `SelectNextMatch`, `SelectPreviousMatch`, `SelectAllMatches`
- Toggles: `ToggleCaseSensitive`, `ToggleWholeWord`, `ToggleRegex`

#### ui.yaml (7 mappings)
- Settings: `OpenSettings`
- Zoom: `IncreaseBufferFontSize`, `DecreaseBufferFontSize`, `ResetBufferFontSize`
- Panels: `NewTerminal`, `ToggleBottomDock`, `ToggleLeftDock`

#### intellisense.yaml (3 mappings)
- Code actions: `ToggleCodeActions`
- Completions: `ShowCompletions`
- Parameter hints: `ShowSignatureHelp`

### 5. Main Configuration

**File**: `src/main/scala/io/septimalmind/magen/Magen.scala`

Added Zed installer to the installers list:
```scala
new ZedInstaller(
  ZedParams(
    List(
      "~/.config/zed/keymap.json",
      "~/work/safe/7mind/nix-config/users/pavel/hm/keymap-zed-linux.json",
    )
  )
)
```

### 6. Documentation

Created comprehensive mapping documentation:
- **2025-11-06-zed-action-mapping.md** - Complete mapping reference between VSCode, IDEA, and Zed actions

## Total Statistics

- **88 Zed actions mapped** across all categories
- **8 YAML files updated** with Zed mappings
- **100% of applicable actions mapped** (only skipped actions with `missing: true` or incomplete definitions)
- **2 output paths configured** (local + nix config)

## Key Design Decisions

1. **No Negation Required**: Unlike IntelliJ, Zed doesn't need explicit unbinding. Unmapped keys remain unbound by default.

2. **Context Simplicity**: Zed uses simpler contexts than VSCode:
   - `Editor` - text editor focus
   - `Terminal` - terminal focus
   - No context - always active

3. **Wildcard Support**: All installers now support glob patterns, making it easy to target multiple IDE versions or user directories.

4. **Consistent Structure**: Zed mappings follow the same YAML structure as VSCode and IDEA for maintainability.

## Testing

To test the implementation:

1. Build the project: Use IntelliJ IDEA or run `sbt compile`
2. Run the main class: `sbt run` or execute `Magen.main()`
3. Check generated files:
   - `~/.config/zed/keymap.json`
   - `~/work/safe/7mind/nix-config/users/pavel/hm/keymap-zed-linux.json`
4. Open Zed editor and verify the keymaps are loaded

## Future Enhancements

Potential improvements:
- Add support for Zed actions with parameters (array format)
- Add Terminal-specific bindings
- Add ProjectPanel-specific bindings
- Support for Zed's action objects with multiple arguments
