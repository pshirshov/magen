package io.septimalmind.magen.cli

import io.septimalmind.magen.model.{Platform, SchemeId}

import java.nio.file.{Path, Paths}

case class ParsedArgs(
  mappingsDir: Option[Path],
  negationsDir: Option[Path],
  scheme: Option[SchemeId],
  platform: Option[Platform],
  keymap: Option[Path],
  keymapId: Option[String],
  positional: List[String],
)

object CliParser {

  /** Splits args into command (first non-flag token) and the rest.
    * All flags (--mappings, --scheme, etc.) can appear anywhere after the command. */
  def parse(args: List[String]): (Option[String], ParsedArgs) = {
    val arr = args.toArray
    var command: Option[String] = None
    var mappingsDir: Option[Path] = None
    var negationsDir: Option[Path] = None
    var scheme: Option[String] = None
    var platform: Option[String] = None
    var keymap: Option[Path] = None
    var keymapId: Option[String] = None
    val positional = scala.collection.mutable.ListBuffer.empty[String]

    var i = 0
    while (i < arr.length) {
      arr(i) match {
        case "--mappings" =>
          assert(i + 1 < arr.length, "--mappings requires a value")
          mappingsDir = Some(Paths.get(arr(i + 1)))
          i += 2
        case "--negations" =>
          assert(i + 1 < arr.length, "--negations requires a value")
          negationsDir = Some(Paths.get(arr(i + 1)))
          i += 2
        case "--scheme" =>
          assert(i + 1 < arr.length, "--scheme requires a value")
          scheme = Some(arr(i + 1))
          i += 2
        case "--platform" =>
          assert(i + 1 < arr.length, "--platform requires a value")
          platform = Some(arr(i + 1))
          i += 2
        case "--keymap" =>
          assert(i + 1 < arr.length, "--keymap requires a value")
          keymap = Some(Paths.get(arr(i + 1)))
          i += 2
        case "--keymap-id" =>
          assert(i + 1 < arr.length, "--keymap-id requires a value")
          keymapId = Some(arr(i + 1))
          i += 2
        case arg if arg.startsWith("--") =>
          throw new IllegalArgumentException(s"Unknown option: $arg")
        case arg =>
          if (command.isEmpty) {
            command = Some(arg)
          } else {
            positional += arg
          }
          i += 1
      }
    }

    val parsed = ParsedArgs(
      mappingsDir = mappingsDir,
      negationsDir = negationsDir,
      scheme = scheme.map(SchemeId.apply),
      platform = platform.map(Platform.parse),
      keymap = keymap,
      keymapId = keymapId,
      positional = positional.toList,
    )
    (command, parsed)
  }
}
