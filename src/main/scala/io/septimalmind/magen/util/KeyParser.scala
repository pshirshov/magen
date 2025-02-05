package io.septimalmind.magen.util

import scala.util.parsing.combinator.RegexParsers
import io.septimalmind.magen.model.*
import io.septimalmind.magen.model.Key.*
import io.septimalmind.magen.model.Modifier.*

object ShortcutParser extends RegexParsers {
  override def skipWhitespace: Boolean = false

  def modifier: Parser[Modifier] =
    ("ctrl" | "alt" | "shift" | "meta") ^^ {
      case "ctrl" => Ctrl
      case "alt" => Alt
      case "shift" => Shift
      case "meta" => Meta
    }

  def key: Parser[NamedKey] =
    ("""[a-zA-Z0-9\[\],.=`/-]+""".r) ^^ {
      s =>
        NamedKey.make(s)
    }

  def chord: Parser[KeyCombo] =
    rep(modifier <~ ("-" | "+")) ~ key ^^ { case mods ~ k => KeyCombo(mods, k) }

  def sequence: Parser[Chord] =
    rep1sep(chord, """\s+""".r).map(combos => Chord(combos))

  def parseShortcuts(input: String): Either[String, Chord] =
    parseAll(sequence, input) match {
      case Success(result, _) => Right(result)
      case NoSuccess(msg, next) => Left(s"Failed to parse at ${next.pos}: $msg")
      case Error(msg, next) => Left(s"Failed to parse at ${next.pos}: $msg")
      case Failure(msg, next) => Left(s"Failed to parse at ${next.pos}: $msg")
    }

  def parseUnsafe(input: String): Chord = {
    parseShortcuts(input).toOption match {
      case Some(value) => value
      case None =>
        println(s"Can't parse $input")
        ???
    }
  }
}

object TestParser extends App {
  println(ShortcutParser.parseAll(ShortcutParser.chord, "ctrl-k"))

  val input = "ctrl-k ctrl-shift-v alt-[Backspace] k [KeyR]"
  ShortcutParser.parseShortcuts(input) match {
    case Right(chords) =>
      println("Parsed shortcut sequence:")
      chords.combos.foreach(println)
    case Left(error) =>
      println(s"Error parsing input: $error")
  }
}
