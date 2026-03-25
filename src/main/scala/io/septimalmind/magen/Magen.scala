package io.septimalmind.magen

import cats.syntax.either.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{yaml, *}
import io.septimalmind.magen.config.MagenConfig
import io.septimalmind.magen.model.*
import io.septimalmind.magen.targets.{IdeaInstaller, IdeaParams, VscodeInstaller, VscodeParams, ZedInstaller, ZedParams}
import io.septimalmind.magen.util.ShortcutParser
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.strings.IzString.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.util.matching.Regex.quoteReplacement

object Magen {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case "generate" :: rest =>
        cmdGenerate(rest)
      case "schemes" :: Nil =>
        cmdSchemes()
      case "render" :: rest =>
        cmdRender(rest)
      case "import" :: rest =>
        cmdImport(rest)
      case "negate-idea" :: rest =>
        cmdNegateIdea(rest)
      case "negate-vscode" :: rest =>
        cmdNegateVscode(rest)
      case Nil =>
        cmdGenerate(Nil)
      case unknown :: _ =>
        System.err.println(s"Unknown command: $unknown")
        printUsage()
        sys.exit(1)
    }
  }

  private def cmdGenerate(args: List[String]): Unit = {
    val config = loadOrCreateConfig()
    val schemeId = parseSchemeArg(args)
      .orElse(config.scheme.map(SchemeId.apply))
      .getOrElse(SchemeId.default)

    println(s"Generating scheme: ${schemeId.name}")
    val converted = loadScheme(schemeId)

    val keymapName = s"Magen-${schemeId.name}"
    val installers = List(
      new VscodeInstaller(
        VscodeParams(
          List("~/.config/VSCodium/User/keybindings.json") ++ config.`installer-paths`.vscode
        )
      ),
      new IdeaInstaller(
        IdeaParams(
          List(s"~/.config/JetBrains/*/keymaps/$keymapName.xml") ++ config.`installer-paths`.idea,
          negate = true,
          parent = "$default",
          keymapName = keymapName,
        )
      ),
      new ZedInstaller(
        ZedParams(
          List("~/.config/zed/keymap.json") ++ config.`installer-paths`.zed
        )
      ),
    )

    installers.foreach(_.install(converted))
  }

  private def cmdRender(args: List[String]): Unit = {
    val config = loadOrCreateConfig()
    val schemeId = parseSchemeArg(args)
      .orElse(config.scheme.map(SchemeId.apply))
      .getOrElse(SchemeId.default)

    val outputDir = args.filterNot(_.startsWith("--")).filterNot(a => args.indexOf(a) > 0 && args(args.indexOf(a) - 1) == "--scheme")
      .headOption.map(Paths.get(_))
      .getOrElse(Paths.get("output"))

    Files.createDirectories(outputDir)

    println(s"Rendering scheme: ${schemeId.name} to $outputDir")
    val converted = loadScheme(schemeId)

    val keymapName = s"Magen-${schemeId.name}"

    val vscodeOut = targets.VSCodeRenderer.render(converted)
    Files.write(outputDir.resolve("keybindings.json"), vscodeOut.getBytes(StandardCharsets.UTF_8))
    println(s"  wrote ${outputDir.resolve("keybindings.json")}")

    val ideaRenderer = new targets.IdeaRenderer(IdeaParams(List.empty, negate = true, parent = "$default", keymapName = keymapName))
    val ideaOut = ideaRenderer.render(converted)
    Files.write(outputDir.resolve(s"$keymapName.xml"), ideaOut.getBytes(StandardCharsets.UTF_8))
    println(s"  wrote ${outputDir.resolve(s"$keymapName.xml")}")

    val zedOut = targets.ZedRenderer.render(converted)
    Files.write(outputDir.resolve("keymap.json"), zedOut.getBytes(StandardCharsets.UTF_8))
    println(s"  wrote ${outputDir.resolve("keymap.json")}")
  }

  private def cmdSchemes(): Unit = {
    val schemes = listSchemes()
    if (schemes.isEmpty) {
      println("No schemes found in mappings/schemes/")
    } else {
      println("Available schemes:")
      schemes.foreach(s => println(s"  ${s.name}"))
    }
  }

  private def cmdImport(args: List[String]): Unit = {
    args match {
      case "vscode" :: rest =>
        cmdImportVscode(rest)
      case "idea" :: rest =>
        cmdImportIdea(rest)
      case "zed" :: rest =>
        cmdImportZed(rest)
      case Nil =>
        System.err.println("Missing import source. Expected: vscode, idea, or zed")
        sys.exit(1)
      case unknown :: _ =>
        System.err.println(s"Unknown import source: $unknown. Expected: vscode, idea, or zed")
        sys.exit(1)
    }
  }

  private def cmdImportVscode(args: List[String]): Unit = {
    val (file, schemeId) = parseImportArgs(args)
    val source = Paths.get(file)
    assert(source.toFile.exists(), s"File not found: $source")
    println(s"Importing VSCode keybindings from $source into scheme '${schemeId.name}'")
    val imported = importer.VscodeSchemeImporter.importFrom(source)
    val out = importer.SchemeWriter.write(schemeId, imported)
    println(s"Done: $out")
  }

  private def cmdImportIdea(args: List[String]): Unit = {
    val (file, keymapId, schemeId) = parseImportArgsWithKeymapId(args)

    (file, keymapId) match {
      case (Some(f), _) =>
        // import from a specific XML file
        val source = Paths.get(f)
        assert(source.toFile.exists(), s"File not found: $source")
        println(s"Importing IntelliJ keybindings from $source into scheme '${schemeId.name}'")
        val imported = importer.IdeaSchemeImporter.importFromFile(source)
        val out = importer.SchemeWriter.write(schemeId, imported)
        println(s"Done: $out")

      case (None, Some(id)) =>
        // import by keymap ID from discovered sources
        println(s"Looking for keymap '$id'...")
        val keymaps = importer.IdeaSchemeImporter.listKeymaps()
        val sources = keymaps.map(_.source).distinct
        val imported = importer.IdeaSchemeImporter.importFrom(id, sources)
        val out = importer.SchemeWriter.write(schemeId, imported)
        println(s"Done: $out")

      case (None, None) =>
        // list mode
        println("Available IntelliJ keymaps:")
        val keymaps = importer.IdeaSchemeImporter.listKeymaps()
        if (keymaps.isEmpty) {
          println("  (none found)")
        } else {
          keymaps.foreach { km =>
            val src = if (km.bundled) "bundled" else km.source.toString
            println(s"  ${km.name} (parent: ${km.parent}, source: $src)")
          }
        }
        println("\nUse --keymap-id <name> to import a specific keymap")
    }
  }

  private def cmdImportZed(args: List[String]): Unit = {
    val (file, schemeId) = parseImportArgs(args)
    val source = Paths.get(file)
    assert(source.toFile.exists(), s"File not found: $source")
    println(s"Importing Zed keybindings from $source into scheme '${schemeId.name}'")
    val imported = importer.ZedSchemeImporter.importFrom(source)
    val out = importer.SchemeWriter.write(schemeId, imported)
    println(s"Done: $out")
  }

  private def cmdNegateIdea(args: List[String]): Unit = {
    val file = args.headOption.map(Paths.get(_))
    val output = Paths.get("mappings", "shared", "idea")
    Files.createDirectories(output)

    file match {
      case Some(f) =>
        // extract from a specific XML file
        assert(f.toFile.exists(), s"File not found: $f")
        println(s"Extracting IDEA actions from $f")
        val actions = importer.IdeaNegationGenerator.extractActionsFromXml(f)
        val outFile = output.resolve("idea-all-actions.json")
        importer.IdeaNegationGenerator.writeActionsJson(actions, outFile)
        println(s"Wrote ${actions.size} actions to $outFile")

      case None =>
        // auto-discover from bundled keymaps
        println("Discovering IDEA installations...")
        val actions = importer.IdeaNegationGenerator.extractFromBundledDefault()
        if (actions.isEmpty) {
          System.err.println("No IDEA installation found. Provide a keymap XML file as argument.")
          sys.exit(1)
        }
        val outFile = output.resolve("idea-all-actions.json")
        importer.IdeaNegationGenerator.writeActionsJson(actions, outFile)
        println(s"Wrote ${actions.size} actions to $outFile")
    }
  }

  private def cmdNegateVscode(args: List[String]): Unit = {
    val file = args.headOption.map(Paths.get(_))
    val output = Paths.get("mappings", "shared", "vscode")
    Files.createDirectories(output)

    file match {
      case Some(f) =>
        assert(f.toFile.exists(), s"File not found: $f")
        println(s"Generating VSCode negations from $f")
        val outFile = output.resolve("vscode-keymap-linux-!negate-all.json")
        importer.VscodeNegationGenerator.generateFromDefaults(f, outFile)
        println(s"Wrote negations to $outFile")

      case None =>
        System.err.println("Provide a VSCode default keybindings JSON file as argument.")
        System.err.println("Export with: code --list-keybindings > vscode-defaults.json")
        sys.exit(1)
    }
  }

  private def parseSchemeArg(args: List[String]): Option[SchemeId] = {
    args match {
      case "--scheme" :: name :: _ => Some(SchemeId(name))
      case _ => None
    }
  }

  private def parseImportArgs(args: List[String]): (String, SchemeId) = {
    val (fileParts, keymapId, scheme) = parseArgsRaw(args)
    val schemeId = scheme.getOrElse {
      System.err.println("--scheme NAME is required for import")
      printUsage()
      sys.exit(1)
    }
    (joinFileParts(fileParts), schemeId)
  }

  private def parseImportArgsWithKeymapId(args: List[String]): (Option[String], Option[String], SchemeId) = {
    val (fileParts, keymapId, scheme) = parseArgsRaw(args)
    val file = if (fileParts.isEmpty) None else Some(joinFileParts(fileParts))
    // scheme is required when actually importing (file or keymapId given), optional for list mode
    val schemeId = scheme.getOrElse {
      if (file.nonEmpty || keymapId.nonEmpty) {
        System.err.println("--scheme NAME is required for import")
        printUsage()
        sys.exit(1)
      }
      SchemeId("_unused_") // list mode, won't be used
    }
    (file, keymapId, schemeId)
  }

  // Collects positional args (file path parts that may have been split on spaces by SBT),
  // named options (--scheme, --keymap-id), and returns them separately.
  private def parseArgsRaw(args: List[String]): (List[String], Option[String], Option[SchemeId]) = {
    val fileParts = scala.collection.mutable.ListBuffer.empty[String]
    var scheme: Option[String] = None
    var keymapId: Option[String] = None
    var i = 0
    val argArr = args.toArray
    while (i < argArr.length) {
      argArr(i) match {
        case "--scheme" =>
          assert(i + 1 < argArr.length, "--scheme requires a value")
          scheme = Some(argArr(i + 1))
          i += 2
        case "--keymap-id" =>
          assert(i + 1 < argArr.length, "--keymap-id requires a value")
          keymapId = Some(argArr(i + 1))
          i += 2
        case arg if !arg.startsWith("--") =>
          fileParts += arg
          i += 1
        case other =>
          System.err.println(s"Unknown option: $other")
          printUsage()
          sys.exit(1)
      }
    }
    (fileParts.toList, keymapId, scheme.map(SchemeId.apply))
  }

  // Joins path parts that were split on spaces, strips wrapping quotes
  private def joinFileParts(parts: List[String]): String = {
    val joined = parts.mkString(" ")
    if ((joined.startsWith("'") && joined.endsWith("'")) || (joined.startsWith("\"") && joined.endsWith("\""))) {
      joined.substring(1, joined.length - 1)
    } else {
      joined
    }
  }

  private def printUsage(): Unit = {
    System.err.println(
      """Usage: magen <command> [options]
        |
        |Commands:
        |  generate [--scheme NAME]                    Generate and install keybindings (default)
        |  render [dir] [--scheme NAME]                Render to directory (default: ./output)
        |  schemes                                     List available schemes
        |  import vscode <file> --scheme NAME          Import VSCode keybindings
        |  import idea [--keymap-id ID] --scheme NAME  Import IntelliJ keybindings
        |  import idea                                 List available IntelliJ keymaps
        |  import zed <file> --scheme NAME             Import Zed keybindings
        |  negate-idea [<keymap.xml>]                  Generate IDEA negation list
        |  negate-vscode <defaults.json>               Generate VSCode negation list
        |""".stripMargin
    )
  }

  // -- Scheme loading --

  def loadScheme(schemeId: SchemeId): Mapping = {
    val schemesDir = Paths.get("mappings", "schemes")
    val schemeDir = schemesDir.resolve(schemeId.name)
    assert(schemeDir.toFile.isDirectory, s"Scheme directory not found: $schemeDir. Available: ${listSchemes().map(_.name).mkString(", ")}")

    val rawMappings = schemeDir.toFile.listFiles()
      .filter(_.getName.endsWith(".yaml"))
      .sorted
      .map(f => readMapping(f.toPath))
      .toList

    convert(rawMappings)
  }

  def listSchemes(): List[SchemeId] = {
    val schemesDir = Paths.get("mappings", "schemes")
    if (!schemesDir.toFile.isDirectory) return List.empty
    schemesDir.toFile.listFiles()
      .filter(_.isDirectory)
      .map(d => SchemeId(d.getName))
      .sorted(Ordering.by[SchemeId, String](_.name))
      .toList
  }

  // -- Config --

  private val configPath: Path = {
    val home = System.getProperty("user.home")
    Paths.get(home, ".config", "magen", "magen.json")
  }

  private def loadOrCreateConfig(): MagenConfig = {
    if (Files.exists(configPath)) {
      val content = IzFiles.readString(configPath)
      parser.parse(content)
        .flatMap(_.as[MagenConfig])
        .getOrElse {
          println(s"Warning: Failed to parse $configPath, using empty config")
          MagenConfig.empty
        }
    } else {
      Files.createDirectories(configPath.getParent)
      val emptyConfig = MagenConfig.empty
      val json = emptyConfig.asJson.spaces2
      Files.write(configPath, json.getBytes(StandardCharsets.UTF_8))
      println(s"Created config file: $configPath")
      emptyConfig
    }
  }

  // -- Mapping conversion --

  private def convert(mapping: List[RawMapping]): Mapping = {
    val allConcepts = mapping.flatMap(_.mapping.toSeq.flatten)
    val bad = allConcepts.map(m => (m.id, m)).toMultimap.filter(_._2.size > 1)
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

        if (Seq(i, v, z).exists(_.nonEmpty) && c.binding.nonEmpty) {
          val chord = NEList
            .unsafeFrom(c.binding)
            .map(expandTemplate(_, vars))
            .map(ShortcutParser.parseUnsafe)
          Seq(Concept(c.id, chord, i, v, z))
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

    var result = template
    var iteration = 0
    var previous = ""

    while (result != previous && iteration < maxIterations) {
      previous = result
      result = pattern.replaceAllIn(
        result,
        m => {
          val key = m.group(1)
          val replacement = values.getOrElse(key, m.matched)
          quoteReplacement(replacement)
        },
      )
      iteration += 1
    }
    result
  }

  def readMapping(path: Path): RawMapping = {
    println(s"Reading $path")
    val input = IzFiles.readString(path)

    val json = yaml.v12.parser.parse(input)

    val mapping = json
      .leftMap(err => err: Error)
      .flatMap(_.as[RawMapping])
      .valueOr(throw _)
    mapping
  }
}
