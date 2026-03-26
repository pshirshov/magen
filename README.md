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

All mapping schemes and shared data are bundled inside the JAR as classpath resources. No external files needed at runtime.

### From source

```bash
# Build the fat JAR
sbt assembly

# Run
java -jar target/scala-2.13/magen.jar schemes
java -jar target/scala-2.13/magen.jar generate --scheme pshirshov
```

## CLI Usage

```
Usage: magen [--mappings DIR] <command> [options]

Global options:
  --mappings DIR  Use mappings from DIR instead of bundled resources

Commands:
  generate [--scheme NAME] [--platform PLATFORM]     Generate and install keybindings (default)
  render [dir] [--scheme NAME] [--platform PLATFORM]  Render to directory (default: ./output)
  schemes                                              List available schemes
  import vscode <file> --scheme NAME                   Import VSCode keybindings
  import idea [--keymap-id ID] --scheme NAME           Import IntelliJ keybindings
  import idea                                          List available IntelliJ keymaps
  import zed <file> --scheme NAME                      Import Zed keybindings
  negate-idea [<keymap.xml>]                           Generate IDEA negation list
  negate-vscode <defaults.json>                        Generate VSCode negation list

Platforms: macos, linux, win (default: auto-detect)
```

### Generate and install keybindings

```bash
# Generate using the default scheme (from config or "pshirshov")
magen generate

# Generate a specific scheme for a specific platform
magen generate --scheme idea-macos --platform linux
```

### Render keybindings to files (without installing)

```bash
# Render to ./output directory
magen render

# Render to a specific directory
magen render /tmp/magen-output --scheme from-idea
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

# Import from IntelliJ -- from a keymap XML file
magen import idea ~/.config/JetBrains/IntelliJIdea2025.2/keymaps/MyKeymap.xml --scheme my-idea

# Import from IntelliJ -- by keymap ID (auto-discovers installed IDEs)
magen import idea --keymap-id '$default' --scheme idea-defaults

# List available IntelliJ keymaps (no --scheme required)
magen import idea
```

Import and negate commands write to the filesystem. By default they write to `./mappings/` in the current directory. Use `--mappings` to specify a different location:

```bash
magen --mappings ./src/main/resources/mappings import idea --keymap-id '$default' --scheme new-scheme
```

### Regenerate negation lists

When Magen generates keybindings, it also **negates** (unbinds) the editor's default shortcuts to prevent conflicts:

- **IntelliJ**: Reads all known action IDs from shared data. Any action not explicitly defined in the scheme gets an empty `<action id="..."/>` entry in the generated keymap XML, removing it from the parent keymap.
- **VSCode**: Prepends negation entries (`{key, command: "-command"}`) to the generated `keybindings.json` that unbind default shortcuts.

These negation lists need to be regenerated when you update your editor or add support for new plugins.

```bash
# IntelliJ: auto-discover installed IDEs
magen negate-idea

# IntelliJ: from a specific keymap XML file
magen negate-idea /path/to/keymap.xml

# VSCode: from exported default keybindings
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

- `scheme` -- default scheme name (overridden by `--scheme` CLI arg)
- `installer-paths` -- additional installation paths beyond the defaults

Default installation paths:
- VSCode: `~/.config/VSCodium/User/keybindings.json`
- IDEA: `~/.config/JetBrains/*/keymaps/Magen-<scheme>.xml`
- Zed: `~/.config/zed/keymap.json`

## Project Structure

```
src/main/resources/mappings/
  schemes/
    pshirshov/          # Default scheme
    idea-macos/         # macOS-oriented IDEA scheme with platform-specific bindings
    from-idea/          # Imported from IDEA defaults
  shared/
    idea/               # IntelliJ action lists (for negation)
    vscode/             # VSCode negation files
```

Schemes and shared data are bundled as classpath resources in the JAR. At runtime, magen reads them from the classpath by default.

## Development

### Local development

```bash
# Enter nix dev shell
nix develop

# Run via SBT (reads resources from classpath automatically)
sbt "run schemes"
sbt "run generate --scheme pshirshov"
sbt "run render /tmp/output --scheme pshirshov"

# Run tests
sbt test
```

### Editing mappings

When developing schemes locally, point `--mappings` to the resources directory so changes are picked up without rebuilding:

```bash
sbt "run --mappings src/main/resources/mappings generate --scheme pshirshov"
sbt "run --mappings src/main/resources/mappings schemes"
```

Import and negate commands also write to the `--mappings` directory, keeping resources in sync:

```bash
sbt "run --mappings src/main/resources/mappings import idea --keymap-id '\$default' --scheme new-scheme"
sbt "run --mappings src/main/resources/mappings negate-idea"
```

### Building

```bash
# Build fat JAR (output: target/scala-2.13/magen.jar)
sbt assembly

# The JAR is self-contained -- run from anywhere
java -jar target/scala-2.13/magen.jar schemes
```

### Regenerate dependency lockfile

```bash
nix develop -c squish-lockfile
```
