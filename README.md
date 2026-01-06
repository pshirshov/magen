# Magen

Magen (MApping GENerator) generates consistent keyboard mappings across multiple editors from a single source of truth.

## Supported Editors

- IntelliJ IDEA (and other JetBrains IDEs)
- VSCode / VSCodium
- Zed

## How It Works

Magen reads YAML mapping files from `mappings/` that define keyboard shortcuts in an editor-agnostic format. Each mapping specifies a binding and editor-specific actions:

```yaml
- id: "editor.action.rename"
  binding:
    - "${group.transform} ctrl+[KeyR]"
  vscode:
    action: 'editor.action.rename'
  zed:
    action: 'editor::Rename'
    context: [ "Editor" ]
  idea:
    action: 'RenameElement'
```

## Installation

```bash
# Run directly with Nix
nix run github:pshirshov/magen

# Or build locally
nix build
./result/bin/magen
```

## Configuration

Additional installation paths can be configured in `~/.config/magen/magen.json`:

```json
{
  "installer-paths": {
    "vscode": [],
    "idea": [],
    "zed": []
  }
}
```

Default installation paths:
- VSCode: `~/.config/VSCodium/User/keybindings.json`
- IDEA: `~/.config/JetBrains/*/keymaps/Magen.xml`
- Zed: `~/.config/zed/keymap.json`

## Development

```bash
# Enter dev shell
nix develop

# Run
sbt run

# Build fat JAR
sbt assembly

# Regenerate dependency lockfile
nix develop -c bash -c "nix run github:7mind/squish-find-the-brains -- lockfile-config.json 2>/dev/null" > deps.lock.json
```

## Mapping Files

| File | Description |
|------|-------------|
| `generic-keys.yaml` | Key group definitions (prefixes) |
| `clipboard.yaml` | Copy, cut, paste |
| `cursor.yaml` | Cursor movement |
| `edit.yaml` | Editing (undo, redo, newline) |
| `selection.yaml` | Text selection |
| `search.yaml` | Find and replace |
| `navigation.yaml` | Go to definition, file, symbol |
| `intellisense.yaml` | Code completion, actions |
| `transform.yaml` | Refactoring |
| `commands.yaml` | File operations |
| `ui.yaml` | UI toggles |
