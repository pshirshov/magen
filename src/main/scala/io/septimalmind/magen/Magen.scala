package io.septimalmind.magen

import cats.syntax.either.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{yaml, *}
import io.septimalmind.magen.cli.{CliParser, ParsedArgs}
import io.septimalmind.magen.config.MagenConfig
import io.septimalmind.magen.model.*
import io.septimalmind.magen.targets.{IdeaInstaller, IdeaParams, VscodeInstaller, VscodeParams, ZedInstaller, ZedParams}
import io.septimalmind.magen.util.{DefaultPaths, MagenPaths, MappingsSource, NegationsSource, PathExpander, ShortcutParser}
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.strings.IzString.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.util.matching.Regex.quoteReplacement

object Magen {
  def main(args: Array[String]): Unit = {
    val (command, parsed) = CliParser.parse(args.toList)
    parsed.mappingsDir.foreach(p => MagenPaths.configure(MappingsSource.Filesystem(p)))
    parsed.negationsDir.foreach(p => MagenPaths.configureNegations(NegationsSource.Filesystem(p)))

    command match {
      case Some("generate") | None =>
        cmdGenerate(parsed)
      case Some("render") =>
        cmdRender(parsed)
      case Some("list") =>
        cmdList()
      case Some("scan") =>
        cmdScan()
      case Some("import") =>
        cmdImport(parsed)
      case Some("import-negation") =>
        cmdImportNegation(parsed)
      case Some(unknown) =>
        System.err.println(s"Unknown command: $unknown")
        printUsage()
        sys.exit(1)
    }
  }

  // -- generate --

  private def cmdGenerate(parsed: ParsedArgs): Unit = {
    val hostPlatform = Platform.detect()
    val config       = loadOrCreateConfig(hostPlatform)
    val platform     = parsed.platform.getOrElse(hostPlatform)
    val schemeId = parsed.scheme
      .orElse(config.scheme.map(SchemeId.apply))
      .getOrElse(SchemeId.default)

    println(s"Generating scheme: ${schemeId.name} (platform: $platform)")
    val (converted, validation) = loadAndValidate(schemeId, platform)
    reportValidation(validation)

    val keymapName = s"Magen-${schemeId.name}"
    val installers = List(
      new VscodeInstaller(
        VscodeParams(
          DefaultPaths.vscodePaths(hostPlatform) ++ config.`installer-paths`.vscode
        ),
        platform,
      ),
      new IdeaInstaller(
        IdeaParams(
          DefaultPaths.ideaPaths(hostPlatform, keymapName) ++ config.`installer-paths`.idea,
          negate     = true,
          parent     = "$default",
          keymapName = keymapName,
        ),
        platform,
      ),
      new ZedInstaller(
        ZedParams(
          DefaultPaths.zedPaths(hostPlatform) ++ config.`installer-paths`.zed
        ),
        platform,
      ),
    )

    installers.foreach(_.install(converted))
  }

  // -- render --

  private def cmdRender(parsed: ParsedArgs): Unit = {
    val config   = loadOrCreateConfig(Platform.detect())
    val platform = parsed.platform.getOrElse(Platform.detect())
    val schemeId = parsed.scheme
      .orElse(config.scheme.map(SchemeId.apply))
      .getOrElse(SchemeId.default)

    val outputDir = parsed.positional.headOption
      .map(Paths.get(_))
      .getOrElse(Paths.get("output"))

    Files.createDirectories(outputDir)

    println(s"Rendering scheme: ${schemeId.name} (platform: $platform) to $outputDir")
    val (converted, validation) = loadAndValidate(schemeId, platform)
    reportValidation(validation)

    val keymapName = s"Magen-${schemeId.name}"

    val vscodeOut = targets.VSCodeRenderer.render(converted, platform)
    Files.write(outputDir.resolve("keybindings.json"), vscodeOut.getBytes(StandardCharsets.UTF_8))
    println(s"  wrote ${outputDir.resolve("keybindings.json")}")

    val ideaRenderer = new targets.IdeaRenderer(IdeaParams(List.empty, negate = true, parent = "$default", keymapName = keymapName))
    val ideaOut      = ideaRenderer.render(converted, platform)
    Files.write(outputDir.resolve(s"$keymapName.xml"), ideaOut.getBytes(StandardCharsets.UTF_8))
    println(s"  wrote ${outputDir.resolve(s"$keymapName.xml")}")

    val zedOut = targets.ZedRenderer.render(converted, platform)
    Files.write(outputDir.resolve("keymap.json"), zedOut.getBytes(StandardCharsets.UTF_8))
    println(s"  wrote ${outputDir.resolve("keymap.json")}")
  }

  // -- list (available schemes) --

  private def cmdList(): Unit = {
    val schemes = listSchemes()
    if (schemes.isEmpty) {
      println("No schemes found")
    } else {
      println("Available schemes:")
      schemes.foreach(s => println(s"  ${s.name}"))
    }
  }

  // -- scan (discover local editor keybindings) --

  private def cmdScan(): Unit = {
    val platform = Platform.detect()
    println(s"Scanning for editor keybindings (platform: $platform)\n")

    println("VSCode / VSCodium:")
    val vscodePaths = PathExpander
      .expandGlobs(DefaultPaths.vscodePaths(platform))
      .filter(p => Files.exists(p) && Files.isRegularFile(p))
    if (vscodePaths.isEmpty) {
      println("  (none found)")
    } else {
      vscodePaths.foreach(p => println(s"  $p"))
    }

    println("\nZed:")
    val zedPaths = PathExpander
      .expandGlobs(DefaultPaths.zedPaths(platform))
      .filter(p => Files.exists(p) && Files.isRegularFile(p))
    if (zedPaths.isEmpty) {
      println("  (none found)")
    } else {
      zedPaths.foreach(p => println(s"  $p"))
    }

    println("\nIntelliJ IDEA:")
    val keymaps = importer.IdeaSchemeImporter.listKeymaps()
    if (keymaps.isEmpty) {
      println("  (none found)")
    } else {
      val (user, bundled) = keymaps.partition(!_.bundled)
      if (user.nonEmpty) {
        println("  User keymaps:")
        user.foreach(km => println(s"    ${km.name} (parent: ${km.parent}, source: ${km.source})"))
      }
      if (bundled.nonEmpty) {
        println("  Bundled keymaps:")
        bundled.foreach(km => println(s"    ${km.name} (parent: ${km.parent}, source: ${km.source})"))
      }
    }
  }

  // -- import --

  private def cmdImport(parsed: ParsedArgs): Unit = {
    requireMappingsDir(parsed)
    val subcommand = parsed.positional.headOption.getOrElse {
      System.err.println("Missing import source. Expected: vscode, idea, or zed")
      printUsage()
      sys.exit(1)
    }
    val subArgs = parsed.copy(positional = parsed.positional.drop(1))
    subcommand match {
      case "vscode" => cmdImportVscode(subArgs)
      case "idea"   => cmdImportIdea(subArgs)
      case "zed"    => cmdImportZed(subArgs)
      case unknown =>
        System.err.println(s"Unknown import source: $unknown. Expected: vscode, idea, or zed")
        printUsage()
        sys.exit(1)
    }
  }

  private def cmdImportVscode(parsed: ParsedArgs): Unit = {
    val schemeId = requireScheme(parsed)
    val source = parsed.keymap.getOrElse {
      discoverEditorKeymap(DefaultPaths.vscodePaths(Platform.detect()), "VSCode/VSCodium")
    }
    println(s"Importing VSCode keybindings from $source into scheme '${schemeId.name}'")
    val imported = importer.VscodeSchemeImporter.importFrom(source)
    val out      = importer.SchemeWriter.write(schemeId, imported)
    println(s"Done: $out")
  }

  private def cmdImportIdea(parsed: ParsedArgs): Unit = {
    val schemeId = requireScheme(parsed)

    (parsed.keymap, parsed.keymapId) match {
      case (Some(f), _) =>
        assert(f.toFile.exists(), s"File not found: $f")
        println(s"Importing IntelliJ keybindings from $f into scheme '${schemeId.name}'")
        val imported = importer.IdeaSchemeImporter.importFromFile(f)
        val out      = importer.SchemeWriter.write(schemeId, imported)
        println(s"Done: $out")

      case (None, Some(id)) =>
        println(s"Looking for keymap '$id'...")
        val keymaps  = importer.IdeaSchemeImporter.listKeymaps()
        val sources  = keymaps.map(_.source).distinct
        val imported = importer.IdeaSchemeImporter.importFrom(id, sources)
        val out      = importer.SchemeWriter.write(schemeId, imported)
        println(s"Done: $out")

      case (None, None) =>
        println("Discovering IDEA keymaps...")
        val keymaps = importer.IdeaSchemeImporter.listKeymaps()
        val defaultKeymap = keymaps.find(km => km.bundled && km.name == "$default").getOrElse {
          val available = keymaps.map(_.name).mkString(", ")
          throw new AssertionError(s"No $$default keymap found. Available: $available")
        }
        println(s"Using keymap '${defaultKeymap.name}' from ${defaultKeymap.source}")
        val sources  = keymaps.map(_.source).distinct
        val imported = importer.IdeaSchemeImporter.importFrom(defaultKeymap.name, sources)
        val out      = importer.SchemeWriter.write(schemeId, imported)
        println(s"Done: $out")
    }
  }

  private def cmdImportZed(parsed: ParsedArgs): Unit = {
    val schemeId = requireScheme(parsed)
    val source = parsed.keymap.getOrElse {
      discoverEditorKeymap(DefaultPaths.zedPaths(Platform.detect()), "Zed")
    }
    println(s"Importing Zed keybindings from $source into scheme '${schemeId.name}'")
    val imported = importer.ZedSchemeImporter.importFrom(source)
    val out      = importer.SchemeWriter.write(schemeId, imported)
    println(s"Done: $out")
  }

  private def discoverEditorKeymap(pathPatterns: List[String], editorName: String): Path = {
    val resolved = PathExpander.expandGlobs(pathPatterns).filter(p => Files.exists(p) && Files.isRegularFile(p))
    resolved.headOption.getOrElse {
      val searched = pathPatterns.mkString(", ")
      throw new AssertionError(s"No $editorName keybindings found. Searched: $searched. Use --keymap <path> to specify.")
    }
  }

  // -- import-negation --

  private def cmdImportNegation(parsed: ParsedArgs): Unit = {
    val negationsDir = requireNegationsDir(parsed)
    val subcommand = parsed.positional.headOption.getOrElse {
      System.err.println("Missing negation source. Expected: idea or vscode")
      printUsage()
      sys.exit(1)
    }
    subcommand match {
      case "idea"   => cmdImportNegationIdea(parsed, negationsDir)
      case "vscode" => cmdImportNegationVscode(parsed, negationsDir)
      case unknown =>
        System.err.println(s"Unknown negation source: $unknown. Expected: idea or vscode")
        printUsage()
        sys.exit(1)
    }
  }

  private def cmdImportNegationIdea(parsed: ParsedArgs, negationsDir: Path): Unit = {
    val output = negationsDir.resolve("idea")
    Files.createDirectories(output)

    parsed.keymap match {
      case Some(f) =>
        assert(f.toFile.exists(), s"File not found: $f")
        println(s"Extracting IDEA actions from $f")
        val actions = importer.IdeaNegationGenerator.extractActionsFromXml(f)
        val outFile = output.resolve("idea-all-actions.json")
        importer.IdeaNegationGenerator.writeActionsJson(actions, outFile)
        println(s"Wrote ${actions.size} actions to $outFile")

      case None =>
        println("Discovering IDEA installations...")
        val actions = importer.IdeaNegationGenerator.extractFromBundledDefault()
        if (actions.isEmpty) {
          println("No IDEA installation found. No negation file generated.")
        } else {
          val outFile = output.resolve("idea-all-actions.json")
          importer.IdeaNegationGenerator.writeActionsJson(actions, outFile)
          println(s"Wrote ${actions.size} actions to $outFile")
        }
    }
  }

  private def cmdImportNegationVscode(parsed: ParsedArgs, negationsDir: Path): Unit = {
    val output = negationsDir.resolve("vscode")
    Files.createDirectories(output)

    parsed.keymap match {
      case Some(f) =>
        assert(f.toFile.exists(), s"File not found: $f")
        println(s"Generating VSCode negations from $f")
        val outFile = output.resolve("vscode-keymap-linux-!negate-all.json")
        importer.VscodeNegationGenerator.generateFromDefaults(f, outFile)
        println(s"Wrote negations to $outFile")

      case None =>
        println("No keymap file provided. No negation file generated.")
        println("Export with: code --list-keybindings > vscode-defaults.json")
    }
  }

  // -- validation helpers --

  private def requireMappingsDir(parsed: ParsedArgs): Unit = {
    if (parsed.mappingsDir.isEmpty) {
      System.err.println("--mappings DIR is required for import commands")
      printUsage()
      sys.exit(1)
    }
  }

  private def requireNegationsDir(parsed: ParsedArgs): Path = {
    parsed.negationsDir.getOrElse {
      System.err.println("--negations DIR is required for import-negation commands")
      printUsage()
      sys.exit(1)
    }
  }

  private def requireScheme(parsed: ParsedArgs): SchemeId = {
    parsed.scheme.getOrElse {
      System.err.println("--scheme NAME is required for import")
      printUsage()
      sys.exit(1)
    }
  }

  // -- usage --

  private def printUsage(): Unit = {
    System.err.println(
      """Usage: magen <command> [options]
        |
        |All options can appear anywhere after the command.
        |
        |Global Options:
        |  --mappings DIR       Directory with scheme mappings (YAML files organized by scheme).
        |                       Default: $MAGEN_MAPPINGS_PATH env var, then bundled classpath resources.
        |  --negations DIR      Directory with negation files (editor action lists for unbinding).
        |                       Default: $MAGEN_NEGATIONS_PATH env var, then bundled classpath resources.
        |                       When set, filesystem is checked first, falling back to classpath
        |                       if a file is missing.
        |  --scheme NAME        Scheme name to use.
        |                       Default: value from config file, or "pshirshov" if not configured.
        |  --platform PLATFORM  Target platform for keybinding generation: macos, linux, win.
        |                       Default: auto-detected from host OS.
        |  --keymap PATH        Path to an editor keymap file (used by import and import-negation).
        |                       Default: auto-discovered from platform-specific editor paths.
        |  --keymap-id ID       IntelliJ keymap identifier (used by import idea).
        |                       Default: "$default" when auto-discovering.
        |
        |Commands:
        |
        |  generate [--scheme NAME] [--platform PLATFORM] [--mappings DIR] [--negations DIR]
        |      Generate keybindings and install them into all supported editors.
        |      Reads the scheme, resolves bindings for the target platform, validates for
        |      conflicts, and writes keybinding files to each editor's config directory.
        |      Installation paths are auto-detected per platform and can be extended
        |      via the config file's "installer-paths" section.
        |      This is the default command when no command is specified.
        |        --scheme     Scheme to generate. Default: from config or "pshirshov".
        |        --platform   Target platform. Default: auto-detected from host OS.
        |        --mappings   Override scheme source directory. Default: classpath.
        |        --negations  Override negation source directory. Default: classpath.
        |
        |  render [DIR] [--scheme NAME] [--platform PLATFORM] [--mappings DIR] [--negations DIR]
        |      Render keybindings to files without installing. Produces keybindings.json
        |      (VSCode), Magen-<scheme>.xml (IDEA), and keymap.json (Zed) in the output
        |      directory.
        |        DIR          Output directory. Default: ./output
        |        --scheme     Scheme to render. Default: from config or "pshirshov".
        |        --platform   Target platform. Default: auto-detected from host OS.
        |        --mappings   Override scheme source directory. Default: classpath.
        |        --negations  Override negation source directory. Default: classpath.
        |
        |  list [--mappings DIR]
        |      List all available scheme names.
        |        --mappings   Override scheme source directory. Default: classpath.
        |
        |  scan
        |      Scan the local filesystem for editor keybinding files. Discovers VSCode,
        |      VSCodium, Zed, and IntelliJ IDEA keybindings using platform-specific default
        |      paths. No options; always uses the auto-detected host platform.
        |
        |  import vscode --scheme NAME --mappings DIR [--keymap PATH]
        |      Import VSCode/VSCodium keybindings as a new Magen scheme.
        |        --scheme     (required) Name for the imported scheme.
        |        --mappings   (required) Directory to write the imported scheme into.
        |        --keymap     Path to keybindings.json. Default: auto-discover from platform
        |                     defaults (VSCodium paths checked before VSCode).
        |
        |  import idea --scheme NAME --mappings DIR [--keymap PATH | --keymap-id ID]
        |      Import IntelliJ IDEA keybindings as a new Magen scheme.
        |        --scheme     (required) Name for the imported scheme.
        |        --mappings   (required) Directory to write the imported scheme into.
        |        --keymap     Path to a keymap XML file. Mutually exclusive with --keymap-id.
        |        --keymap-id  Keymap identifier to import (e.g. "$default", "Eclipse").
        |                     Default (when neither --keymap nor --keymap-id given):
        |                     auto-discover installed IDEs and use the "$default" keymap.
        |
        |  import zed --scheme NAME --mappings DIR [--keymap PATH]
        |      Import Zed keybindings as a new Magen scheme.
        |        --scheme     (required) Name for the imported scheme.
        |        --mappings   (required) Directory to write the imported scheme into.
        |        --keymap     Path to keymap.json. Default: auto-discover from platform defaults.
        |
        |  import-negation idea --negations DIR [--keymap PATH]
        |      Generate an IDEA negation list (all action IDs) for unbinding defaults.
        |      Output: <negations>/idea/idea-all-actions.json
        |        --negations  (required) Directory to write negation files into.
        |        --keymap     Path to an IDEA keymap XML file. Default: auto-discover from
        |                     installed JetBrains IDEs via bundled default extraction.
        |
        |  import-negation vscode --negations DIR --keymap PATH
        |      Generate a VSCode negation list for unbinding defaults.
        |      Output: <negations>/vscode/vscode-keymap-linux-!negate-all.json
        |        --negations  (required) Directory to write negation files into.
        |        --keymap     (required) Path to exported VSCode default keybindings.
        |                     Export with: code --list-keybindings > vscode-defaults.json
        |                     No auto-discovery available for this command.
        |""".stripMargin
    )
  }

  // -- Validation --

  private def reportValidation(result: ValidationResult): Unit = {
    val missing      = result.missingBindings
    val ideaWarnings = result.ideaConflicts
    val errors       = result.errors

    if (missing.nonEmpty) {
      System.err.println(s"\nMissing platform bindings (${missing.size}):")
      missing.foreach(m => System.err.println(s"  ${m.message}"))
    }

    if (ideaWarnings.nonEmpty) {
      System.err.println(s"\nIDEA binding conflicts (${ideaWarnings.size}, warnings - IDEA resolves by runtime context):")
      ideaWarnings.foreach(c => System.err.println(s"  ${c.message}"))
    }

    if (errors.nonEmpty) {
      System.err.println(s"\nBinding conflicts (${errors.size}):")
      errors.foreach(c => System.err.println(s"  ${c.message}"))
      System.err.println()
      assert(false, s"${errors.size} binding conflict(s) found, aborting")
    }
  }

  // -- Scheme loading --

  def loadScheme(schemeId: SchemeId, platform: Platform): Mapping = {
    loadAndValidate(schemeId, platform)._1
  }

  def loadAndValidate(schemeId: SchemeId, platform: Platform): (Mapping, ValidationResult) = {
    val schemeFiles = MagenPaths.listSchemeFiles(schemeId.name)
    assert(schemeFiles.nonEmpty, s"Scheme not found or empty: ${schemeId.name}. Available: ${listSchemes().map(_.name).mkString(", ")}")

    val rawMappings = schemeFiles.sorted.map {
      fileName =>
        println(s"Reading ${schemeId.name}/$fileName")
        readMappingFromString(MagenPaths.readSchemeFile(schemeId.name, fileName))
    }

    val rawConcepts = rawMappings.flatMap(_.mapping.toSeq.flatten)
    val mapping     = convert(rawMappings, platform)
    val result      = MappingValidator.validate(mapping, rawConcepts, platform)
    (mapping, result)
  }

  def listSchemes(): List[SchemeId] = {
    MagenPaths.listSchemes().map(SchemeId.apply)
  }

  // -- Config --

  private def loadOrCreateConfig(hostPlatform: Platform): MagenConfig = {
    val cfgPath = DefaultPaths.configPath(hostPlatform)
    if (Files.exists(cfgPath)) {
      val content = IzFiles.readString(cfgPath)
      parser
        .parse(content)
        .flatMap(_.as[MagenConfig])
        .getOrElse {
          println(s"Warning: Failed to parse $cfgPath, using empty config")
          MagenConfig.empty
        }
    } else {
      Files.createDirectories(cfgPath.getParent)
      val emptyConfig = MagenConfig.empty
      val json        = emptyConfig.asJson.spaces2
      Files.write(cfgPath, json.getBytes(StandardCharsets.UTF_8))
      println(s"Created config file: $cfgPath")
      emptyConfig
    }
  }

  // -- Mapping conversion --

  private def convert(mapping: List[RawMapping], platform: Platform): Mapping = {
    val allConcepts = mapping.flatMap(_.mapping.toSeq.flatten)
    val bad         = allConcepts.map(m => (m.id, m)).toMultimap.filter(_._2.size > 1)
    if (bad.nonEmpty) {
      println(s"Conflicts: ${bad.niceList()}")
      ???
    }

    import izumi.fundamentals.collections.IzCollections.*
    val allKeys = mapping.flatMap(_.keys.map(_.toSeq).toSeq.flatten).toUniqueMap(identity)

    val vars = allKeys match {
      case Left(value) =>
        println(s"Conflicts: $value")
        ???
      case Right(value) =>
        value
    }

    val concepts = allConcepts.flatMap {
      c =>
        val resolvedBinding = c.binding.resolve(platform)

        val i = c.idea.flatMap(i => i.action.map(a => IdeaAction(a, i.mouse.toList.flatten)))
        val v = c.vscode.flatMap(
          i =>
            i.action.map {
              a =>
                val bindings = i.binding.toList.flatten.map(expandTemplate(_, vars)).map(ShortcutParser.parseUnsafe)

                VSCodeAction(a, i.context.toList.flatten, bindings)
            }
        )
        val z = c.zed.flatMap(i => i.action.map(a => ZedAction(a, i.context.toList.flatten)))

        if (i.isEmpty && !c.idea.exists(_.missing.contains(true))) {
          println(s"${c.id}: not defined for IDEA")
        }
        if (v.isEmpty && !c.vscode.exists(_.missing.contains(true))) {
          println(s"${c.id}: not defined for VSCode")
        }
        if (z.isEmpty && !c.zed.exists(_.missing.contains(true)) && false) {
          println(s"${c.id}: not defined for Zed")
        }

        val hasMouseOnly = resolvedBinding.isEmpty && i.exists(_.mouse.nonEmpty)

        if (Seq(i, v, z).exists(_.nonEmpty) && resolvedBinding.nonEmpty) {
          val chord = NEList
            .unsafeFrom(resolvedBinding)
            .map(expandTemplate(_, vars))
            .map(ShortcutParser.parseUnsafe)
          Seq(Concept(c.id, chord, i, v, z))
        } else if (hasMouseOnly) {
          val placeholder = NEList(Chord(List.empty))
          Seq(Concept(c.id, placeholder, i, None, None))
        } else if (!c.unset.contains(true)) {
          println(s"Incomplete definition: ${c.id}")
          Seq.empty
        } else {
          Seq.empty
        }
    }

    Mapping(concepts.sortBy(_.id))
  }

  def expandTemplate(
    template: String,
    values: Map[String, String],
    maxIterations: Int = 10,
  ): String = {
    val pattern = """\$\{([^}]+)\}""".r

    var result    = template
    var iteration = 0
    var previous  = ""

    while (result != previous && iteration < maxIterations) {
      previous = result
      result = pattern.replaceAllIn(
        result,
        m => {
          val key         = m.group(1)
          val replacement = values.getOrElse(key, m.matched)
          quoteReplacement(replacement)
        },
      )
      iteration += 1
    }
    result
  }

  def readMappingFromString(input: String): RawMapping = {
    val json = yaml.v12.parser.parse(input)
    json
      .leftMap(err => err: Error)
      .flatMap(_.as[RawMapping])
      .valueOr(throw _)
  }

  def readMapping(path: Path): RawMapping = {
    println(s"Reading $path")
    val input = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    readMappingFromString(input)
  }
}
