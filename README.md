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

Bindings can be universal or platform-specific:

```yaml
# Universal binding (same on all platforms)
- id: "cursorLeft"
  binding:
    - "left"

# Platform-specific binding
- id: "paste"
  binding:
    macos: "meta+[KeyV]"
    default: "ctrl+[KeyV]"

# Platform-specific with per-platform lists
- id: "resume"
  binding:
    macos:
      - "meta+alt+[KeyR]"
      - "[F9]"
    default:
      - "ctrl+alt+[KeyR]"
      - "[F9]"
```

Platform keys: `macos`, `linux`, `win`, `default` (fallback when a specific platform is not defined).

## Installation

### Nix (recommended)

```bash
# Run directly
nix run github:7mind/magen

# Install to profile
nix profile install github:7mind/magen

# Use as flake input
{
  inputs.magen.url = "github:7mind/magen";
}
```

All mapping schemes and negation data are bundled inside the JAR as classpath resources. No external files needed at runtime.

### From source

```bash
# Build the fat JAR
sbt assembly

# Run
java -jar target/scala-2.13/magen.jar list
java -jar target/scala-2.13/magen.jar generate --scheme pshirshov
```

## CLI Usage

```
magen <command> [options]
```

All options can appear anywhere after the command.

### Global Options

| Option | Description | Default |
|--------|-------------|---------|
| `--mappings DIR` | Directory with scheme mappings (YAML files organized by scheme) | Bundled classpath resources |
| `--negations DIR` | Directory with negation files (editor action lists for unbinding). Filesystem is checked first, falling back to classpath if a file is missing | Bundled classpath resources |
| `--scheme NAME` | Scheme name | Value from config file, or `pshirshov` |
| `--platform PLATFORM` | Target platform for keybinding generation: `macos`, `linux`, `win` | Auto-detected from host OS |
| `--keymap PATH` | Path to an editor keymap file (used by `import` and `import-negation`) | Auto-discovered from platform-specific editor paths |
| `--keymap-id ID` | IntelliJ keymap identifier (used by `import idea`) | `$default` when auto-discovering |

### Commands

#### `generate` -- Generate and install keybindings

Reads the scheme, resolves bindings for the target platform, validates for conflicts, and writes keybinding files to each editor's config directory. Installation paths are auto-detected per platform and can be extended via the config file's `installer-paths` section. This is the **default command** when no command is specified.

```
magen generate [--scheme NAME] [--platform PLATFORM] [--mappings DIR] [--negations DIR]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--scheme NAME` | No | From config file, or `pshirshov` |
| `--platform PLATFORM` | No | Auto-detected from host OS |
| `--mappings DIR` | No | Bundled classpath resources |
| `--negations DIR` | No | Bundled classpath resources |

```bash
# Generate using the default scheme
magen generate

# Generate a specific scheme for a specific platform
magen generate --scheme idea-macos --platform linux
```

#### `render` -- Render keybindings to files

Render keybindings to files without installing. Produces `keybindings.json` (VSCode), `Magen-<scheme>.xml` (IDEA), and `keymap.json` (Zed) in the output directory.

```
magen render [DIR] [--scheme NAME] [--platform PLATFORM] [--mappings DIR] [--negations DIR]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `DIR` (positional) | No | `./output` |
| `--scheme NAME` | No | From config file, or `pshirshov` |
| `--platform PLATFORM` | No | Auto-detected from host OS |
| `--mappings DIR` | No | Bundled classpath resources |
| `--negations DIR` | No | Bundled classpath resources |

```bash
# Render to ./output directory
magen render

# Render to a specific directory
magen render /tmp/magen-output --scheme from-idea
```

#### `list` -- List available schemes

```
magen list [--mappings DIR]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--mappings DIR` | No | Bundled classpath resources |

```bash
magen list
```

#### `scan` -- Scan for local editor keybindings

Discovers keybinding files on the local filesystem for all supported editors (VSCode, VSCodium, Zed, IntelliJ IDEA) using platform-specific default paths. No options; always uses the auto-detected host platform.

```
magen scan
```

```bash
magen scan
```

#### `import vscode` -- Import VSCode/VSCodium keybindings

Import VSCode or VSCodium keybindings as a new Magen scheme. When `--keymap` is omitted, auto-discovers keybindings from platform default paths (VSCodium paths are checked before VSCode).

```
magen import vscode --scheme NAME --mappings DIR [--keymap PATH]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--scheme NAME` | **Yes** | -- |
| `--mappings DIR` | **Yes** | -- |
| `--keymap PATH` | No | Auto-discover from platform defaults (VSCodium > VSCode) |

```bash
# Auto-discover from platform defaults
magen import vscode --scheme my-vscode --mappings src/main/resources/mappings

# From a specific file
magen import vscode --keymap ~/Library/Application\ Support/Code/User/keybindings.json \
  --scheme my-vscode --mappings src/main/resources/mappings
```

#### `import idea` -- Import IntelliJ keybindings

Import IntelliJ IDEA keybindings as a new Magen scheme. Supports three modes: direct file path (`--keymap`), keymap ID lookup (`--keymap-id`), or auto-discovery (neither specified).

**Parent keymap resolution:** IDEA keymaps use a parent inheritance chain (e.g. `Sublime Text (Mac OS X)` → `Mac OS X 10.5+` → `$default`). Magen automatically resolves the full parent chain, merging inherited bindings: child shortcuts override parent shortcuts for the same action, and empty `<action/>` elements in a child unbind that action from the parent. Bundled default keymaps (`$default`, `Mac OS X 10.5+`, `Emacs`, etc.) are included as resources and used for parent resolution. If a parent keymap is not found, a warning is printed and only the keymap's own bindings are imported.

```
magen import idea --scheme NAME --mappings DIR [--keymap PATH | --keymap-id ID]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--scheme NAME` | **Yes** | -- |
| `--mappings DIR` | **Yes** | -- |
| `--keymap PATH` | No | Mutually exclusive with `--keymap-id` |
| `--keymap-id ID` | No | When neither `--keymap` nor `--keymap-id` given: auto-discover installed IDEs and use `$default` keymap |

```bash
# Auto-discover $default keymap from installed IDEs
magen import idea --scheme my-idea --mappings src/main/resources/mappings

# Import a specific keymap by ID
magen import idea --keymap-id '$default' --scheme idea-defaults --mappings src/main/resources/mappings

# Import from a specific XML file
magen import idea --keymap /path/to/keymap.xml --scheme from-file --mappings src/main/resources/mappings
```

#### `import zed` -- Import Zed keybindings

Import Zed keybindings as a new Magen scheme.

```
magen import zed --scheme NAME --mappings DIR [--keymap PATH]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--scheme NAME` | **Yes** | -- |
| `--mappings DIR` | **Yes** | -- |
| `--keymap PATH` | No | Auto-discover from platform defaults |

```bash
# Auto-discover
magen import zed --scheme my-zed --mappings src/main/resources/mappings

# From a specific file
magen import zed --keymap ~/.config/zed/keymap.json --scheme my-zed --mappings src/main/resources/mappings
```

#### `import-negation idea` -- Generate IDEA negation list

Extract all action IDs from an IntelliJ keymap for use as a negation list. When Magen generates IDEA keybindings, any action not explicitly defined in the scheme gets an empty `<action id="..."/>` entry in the keymap XML, removing it from the parent keymap.

Output: `<negations>/idea/idea-all-actions.json`

```
magen import-negation idea --negations DIR [--keymap PATH]
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--negations DIR` | **Yes** | -- |
| `--keymap PATH` | No | Auto-discover from installed JetBrains IDEs via bundled default extraction. If no IDE found, no file is generated |

```bash
# Auto-discover installed IDEs
magen import-negation idea --negations src/main/resources/negations

# From a specific keymap XML file
magen import-negation idea --keymap /path/to/keymap.xml --negations src/main/resources/negations
```

#### `import-negation vscode` -- Generate VSCode negation list

Generate VSCode negation entries for unbinding default shortcuts. Prepends `{key, command: "-command"}` entries to the generated `keybindings.json`.

Output: `<negations>/vscode/vscode-keymap-linux-!negate-all.json`

```
magen import-negation vscode --negations DIR --keymap PATH
```

| Parameter | Required | Default |
|-----------|----------|---------|
| `--negations DIR` | **Yes** | -- |
| `--keymap PATH` | **Yes** | No auto-discovery. Export with: `code --list-keybindings > vscode-defaults.json` |

```bash
# From exported default keybindings
magen import-negation vscode --keymap /path/to/vscode-defaults.json --negations src/main/resources/negations
```

Negation lists should be regenerated when you update your editor or add support for new plugins.

## Configuration

Config file location is platform-dependent:

| Platform | Path |
|----------|------|
| macOS    | `~/Library/Application Support/magen/magen.json` |
| Linux    | `~/.config/magen/magen.json` |
| Windows  | `~/AppData/Roaming/magen/magen.json` |

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

- `scheme` -- default scheme name (overridden by `--scheme` CLI arg)
- `installer-paths` -- additional installation paths beyond the defaults

### Default installation paths

Magen auto-detects the host platform and uses the correct paths for each editor.

**macOS:**
- VSCodium: `~/Library/Application Support/VSCodium/User/keybindings.json`
- VSCode: `~/Library/Application Support/Code/User/keybindings.json`
- IDEA: `~/Library/Application Support/JetBrains/*/keymaps/Magen-<scheme>.xml`
- Zed: `~/.config/zed/keymap.json`

**Linux:**
- VSCodium: `~/.config/VSCodium/User/keybindings.json`
- VSCode: `~/.config/Code/User/keybindings.json`
- IDEA: `~/.config/JetBrains/*/keymaps/Magen-<scheme>.xml`
- Zed: `~/.config/zed/keymap.json`

**Windows:**
- VSCodium: `~/AppData/Roaming/VSCodium/User/keybindings.json`
- VSCode: `~/AppData/Roaming/Code/User/keybindings.json`
- IDEA: `~/AppData/Roaming/JetBrains/*/keymaps/Magen-<scheme>.xml`
- Zed: `~/AppData/Roaming/Zed/keymap.json`

## Project Structure

```
src/main/resources/
  mappings/               # --mappings reads from here
    pshirshov/            # Default scheme
    idea-macos/           # macOS-oriented IDEA scheme with platform-specific bindings
    from-idea/            # Imported from IDEA defaults
  negations/              # --negations reads/writes here
    idea/                 # IntelliJ action lists (for negation)
    vscode/               # VSCode negation files
  editor-mappings/        # Cross-editor action equivalences
    from-idea.json        # IDEA action → VSCode/Zed equivalents
    from-vscode.json      # VSCode action → IDEA/Zed equivalents
    from-zed.json         # Zed action → IDEA/VSCode equivalents
  idea-keymaps/           # Bundled IntelliJ default keymaps (for parent resolution)
    $default.xml          # Root keymap with all default bindings
    Mac OS X 10.5+.xml    # macOS keymap (parent: $default)
    Emacs.xml             # Emacs keymap (parent: $default)
    ...                   # Other bundled keymaps from IntelliJ Community
```

Schemes and negation data are bundled as classpath resources in the JAR. At runtime, magen reads them from the classpath by default. Use `--mappings` and `--negations` to override with filesystem directories.

## Development

### Local development

```bash
# Enter nix dev shell
nix develop

# Run via SBT (reads resources from classpath automatically)
sbt "run list"
sbt "run generate --scheme pshirshov"
sbt "run render /tmp/output --scheme pshirshov"

# Run tests
sbt test
```

### Editing mappings

When developing locally, point `--mappings` and `--negations` to the resources directories so changes are picked up without rebuilding:

```bash
sbt "run generate --scheme pshirshov --mappings src/main/resources/mappings --negations src/main/resources/negations"
sbt "run list --mappings src/main/resources/mappings"
```

Import commands write to the `--mappings` directory, import-negation commands write to the `--negations` directory:

```bash
sbt "run import idea --keymap-id '\$default' --scheme new-scheme --mappings src/main/resources/mappings"
sbt "run import-negation idea --negations src/main/resources/negations"
```

### Building

```bash
# Build fat JAR (output: target/scala-2.13/magen.jar)
sbt assembly

# The JAR is self-contained -- run from anywhere
java -jar target/scala-2.13/magen.jar list
```

### Regenerate dependency lockfile

```bash
nix develop -c squish-lockfile
```
