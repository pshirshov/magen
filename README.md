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

## Quick Start

```bash
# Build the fat JAR
./build.sh

# Run magen via the wrapper script
./magen.sh generate
./magen.sh schemes
./magen.sh render /tmp/magen-output
```

The `magen.sh` wrapper runs from the project root so that `mappings/` paths resolve correctly. Scheme directories are **not** embedded in the JAR — they live on disk and are read at runtime.

## CLI Usage

All examples below use `./magen.sh`. Replace with `magen` if using a Nix installation, or `sbt run` during development.

### Generate and install keybindings

```bash
# Generate using the default scheme (from config or "pshirshov")
./magen.sh generate

# Generate a specific scheme
./magen.sh generate --scheme pshirshov
```

### Render keybindings to files (without installing)

```bash
# Render to ./output directory
./magen.sh render

# Render to a specific directory
./magen.sh render /tmp/magen-output

# Render a specific scheme
./magen.sh render /tmp/magen-output --scheme from-idea
```

### List available schemes

```bash
./magen.sh schemes
```

### Import keybindings from editors

Import an editor's native keybindings as a new Magen scheme:

```bash
# Import from VSCode / VSCodium
./magen.sh import vscode ~/.config/VSCodium/User/keybindings.json --scheme my-vscode

# Import from Zed
./magen.sh import zed ~/.config/zed/keymap.json --scheme my-zed

# Import from IntelliJ — from a keymap XML file
./magen.sh import idea ~/.config/JetBrains/IntelliJIdea2025.2/keymaps/MyKeymap.xml --scheme my-idea

# Import from IntelliJ — by keymap ID (auto-discovers installed IDEs)
./magen.sh import idea --keymap-id '$default' --scheme idea-defaults

# List available IntelliJ keymaps (no --scheme required)
./magen.sh import idea
```

Imported schemes are written to `mappings/schemes/<name>/imported.yaml`. The imported editor's actions are filled in; other editors are marked `missing: true`.

### Regenerate negation lists

When Magen generates keybindings, it also **negates** (unbinds) the editor's default shortcuts to prevent conflicts. This works differently per editor:

- **IntelliJ**: Magen reads all known action IDs from `mappings/shared/idea/idea-all-actions.json`. Any action not explicitly defined in the scheme gets an empty `<action id="..."/>` entry in the generated keymap XML, which removes it from the parent keymap.
- **VSCode**: Magen prepends entries from `mappings/shared/vscode/vscode-keymap-linux-!negate-*.json` to the generated `keybindings.json`. These are `{key, command: "-command"}` entries that unbind default shortcuts.

These negation lists need to be regenerated when you update your editor (new actions/shortcuts appear) or when adding support for new plugins.

#### IntelliJ negation list

```bash
# Auto-discover installed IDEs, extract $default keymap action IDs
./magen.sh negate-idea

# Or extract from a specific keymap XML file
./magen.sh negate-idea /path/to/keymap.xml
```

This writes `mappings/shared/idea/idea-all-actions.json`. For plugin-specific actions (e.g. Continue), maintain separate files like `continue-all-actions.json` in the same directory.

The original manual workflow this replaces:
```bash
unzip -p "$(dirname $(readlink -f $(which idea-ultimate)))/../idea-ultimate/lib/app-client.jar" 'keymaps/$default.xml' \
  | xmlstarlet sel -t -v '//action/@id' \
  | sort \
  | python -c "import sys,json; print(json.dumps([line.strip() for line in sys.stdin]))" \
  | jq > ./mappings/shared/idea/idea-all-actions.json
```

#### VSCode negation list

```bash
# First, export VSCode/VSCodium default keybindings to a JSON file.
# Open VSCode, run "Preferences: Open Default Keyboard Shortcuts (JSON)",
# and save the contents to a file. Then:
./magen.sh negate-vscode /path/to/vscode-defaults.json
```

This writes `mappings/shared/vscode/vscode-keymap-linux-!negate-all.json`. Plugin-specific negation files (e.g. `!negate-gitlens.json`, `!negate-continue.json`) are maintained separately — the VSCode renderer loads all `!negate-*.json` files from that directory.

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

## Building

```bash
# Build fat JAR (output: target/scala-2.13/magen.jar)
./build.sh

# Or manually
sbt assembly

# Run via SBT during development (no build step needed)
sbt "run generate"
sbt "run schemes"
sbt "run render /tmp/output --scheme pshirshov"
```

## Development

```bash
# Enter dev shell
nix develop

# Run directly
sbt run

# Regenerate dependency lockfile
nix develop -c bash -c "nix run github:7mind/squish-find-the-brains -- lockfile-config.json 2>/dev/null" > deps.lock.json
```
