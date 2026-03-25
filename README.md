# Magen

Magen (MApping GENerator) generates consistent keyboard mappings across multiple editors from a single source of truth.

## Supported Editors

- IntelliJ IDEA (and other JetBrains IDEs)
- VSCode / VSCodium
- Zed

## How It Works

Magen reads YAML mapping files that define keyboard shortcuts in an editor-agnostic format. Each mapping specifies a binding and editor-specific actions:

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

Mappings are organized into **schemes** under `mappings/schemes/<name>/`. The default scheme is `pshirshov`.

## Installation

```bash
# Run directly with Nix
nix run github:pshirshov/magen

# Or build locally
nix build
./result/bin/magen

# Or with SBT
sbt run
```

## CLI Usage

### Generate and install keybindings

```bash
# Generate using the default scheme (from config or "pshirshov")
magen generate

# Generate a specific scheme
magen generate --scheme pshirshov
```

### Render keybindings to files (without installing)

```bash
# Render to ./output directory
magen render

# Render to a specific directory
magen render /tmp/magen-output

# Render a specific scheme
magen render /tmp/magen-output --scheme pshirshov
```

### List available schemes

```bash
magen schemes
```

### Import keybindings from editors

Import an editor's native keybindings as a new Magen scheme:

```bash
# Import from VSCode / VSCodium
magen import vscode ~/.config/VSCodium/User/keybindings.json --scheme my-vscode

# Import from Zed
magen import zed ~/.config/zed/keymap.json --scheme my-zed

# Import from IntelliJ — from a keymap XML file
magen import idea ~/.config/JetBrains/IntelliJIdea2025.2/keymaps/MyKeymap.xml --scheme my-idea

# Import from IntelliJ — by keymap ID (auto-discovers installed IDEs)
magen import idea --keymap-id '$default' --scheme idea-defaults

# List available IntelliJ keymaps (no --scheme required)
magen import idea
```

Imported schemes are written to `mappings/schemes/<name>/imported.yaml`. The imported editor's actions are filled in; other editors are marked `missing: true`.

### Generate negation lists

Negation lists suppress default editor keybindings so they don't conflict with Magen bindings.

```bash
# Generate IDEA negation list from a keymap XML
magen negate-idea /path/to/keymap.xml

# Auto-discover from installed IDEs
magen negate-idea

# Generate VSCode negation list from default keybindings export
magen negate-vscode /path/to/vscode-defaults.json
```

## Configuration

`~/.config/magen/magen.json`:

```json
{
  "scheme": "pshirshov",
  "installer-paths": {
    "vscode": [],
    "idea": [],
    "zed": []
  }
}
```

- `scheme` — default scheme name (overridden by `--scheme` CLI arg)
- `installer-paths` — additional installation paths beyond the defaults

Default installation paths:
- VSCode: `~/.config/VSCodium/User/keybindings.json`
- IDEA: `~/.config/JetBrains/*/keymaps/Magen-<scheme>.xml`
- Zed: `~/.config/zed/keymap.json`

## Project Structure

```
mappings/
  schemes/
    pshirshov/          # Default scheme — YAML mapping files
      generic-keys.yaml # Key group definitions (prefixes)
      clipboard.yaml    # Copy, cut, paste
      cursor.yaml       # Cursor movement
      edit.yaml         # Editing (undo, redo, newline)
      selection.yaml    # Text selection
      search.yaml       # Find and replace
      navigation.yaml   # Go to definition, file, symbol
      intellisense.yaml # Code completion, actions
      transform.yaml    # Refactoring
      commands.yaml     # File operations
      ui.yaml           # UI toggles
      ...
  shared/
    idea/               # IntelliJ action lists (for negation)
    vscode/             # VSCode negation files
```

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
