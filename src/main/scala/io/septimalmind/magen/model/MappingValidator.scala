package io.septimalmind.magen.model

import io.septimalmind.magen.util.Aliases

sealed trait ValidationIssue {
  def message: String
}

case class ConflictEntry(conceptId: String, action: String)

case class BindingConflict(
  chord: Chord,
  editor: String,
  context: String,
  entries: List[ConflictEntry],
) extends ValidationIssue {
  def message: String = {
    val chordStr = chord.combos
      .map(c => (c.modifiers.map(_.toString.toLowerCase) :+ c.key.name).mkString("+"))
      .mkString(" ")
    val ctx = if (context.nonEmpty) s" (context: $context)" else ""
    val details = entries.map(e => s"${e.conceptId} -> ${e.action}").mkString(", ")
    s"[$editor] Binding conflict on '$chordStr'$ctx: $details"
  }
}

case class MissingPlatformBinding(
  conceptId: String,
  platform: Platform,
) extends ValidationIssue {
  def message: String = s"Concept '$conceptId' has no binding for platform $platform"
}

case class ValidationResult(issues: List[ValidationIssue]) {
  def conflicts: List[BindingConflict] = issues.collect { case c: BindingConflict => c }
  def missingBindings: List[MissingPlatformBinding] = issues.collect { case m: MissingPlatformBinding => m }

  /** IDEA supports multiple actions per key (resolved at runtime by context),
    * so IDEA conflicts are warnings, not errors. */
  def ideaConflicts: List[BindingConflict] = conflicts.filter(_.editor == "idea")

  /** VSCode and Zed conflicts within the same context are real errors. */
  def errors: List[BindingConflict] = conflicts.filter(_.editor != "idea")

  def hasErrors: Boolean = errors.nonEmpty
}

object MappingValidator {

  def validate(
    mapping: Mapping,
    rawConcepts: List[RawConcept],
    platform: Platform,
  ): ValidationResult = {
    val conflicts = validateConflicts(mapping)
    val missing = validateCompleteness(rawConcepts, platform)
    ValidationResult(conflicts ++ missing)
  }

  def validateConflicts(mapping: Mapping): List[BindingConflict] = {
    validateIdeaConflicts(mapping) ++
      validateVscodeConflicts(mapping) ++
      validateZedConflicts(mapping)
  }

  def validateCompleteness(
    rawConcepts: List[RawConcept],
    platform: Platform,
  ): List[MissingPlatformBinding] = {
    rawConcepts.flatMap { c =>
      if (c.unset.contains(true)) {
        None
      } else {
        val hasEditor = c.idea.nonEmpty || c.vscode.nonEmpty || c.zed.nonEmpty
        val resolved = c.binding.resolve(platform)
        if (hasEditor && resolved.isEmpty) {
          Some(MissingPlatformBinding(c.id, platform))
        } else {
          None
        }
      }
    }
  }

  private def validateIdeaConflicts(mapping: Mapping): List[BindingConflict] = {
    val entries = for {
      c <- mapping.mapping
      i <- c.idea.toList
      b <- c.binding.toList.flatMap(Aliases.extend)
      if b.combos.nonEmpty
    } yield (normalizeChord(b), ConflictEntry(c.id, i.action))

    findConflicts(entries, "idea")
  }

  private def validateVscodeConflicts(mapping: Mapping): List[BindingConflict] = {
    val entries = for {
      c <- mapping.mapping
      a <- c.vscode.toList
      b <- (c.binding ++ a.binding).toList.flatMap(Aliases.extend)
      ctx <- if (a.context.nonEmpty) a.context else List("")
    } yield ((normalizeChord(b), ctx), ConflictEntry(c.id, a.action))

    entries
      .groupBy(_._1)
      .toList
      .flatMap {
        case ((chord, ctx), pairs) =>
          val uniqueActions = pairs.map(_._2).distinctBy(_.action)
          if (uniqueActions.size > 1) {
            List(BindingConflict(chord, "vscode", ctx, uniqueActions))
          } else {
            List.empty
          }
      }
  }

  private def validateZedConflicts(mapping: Mapping): List[BindingConflict] = {
    val entries = for {
      c <- mapping.mapping
      a <- c.zed.toList
      b <- c.binding.toList.flatMap(Aliases.extend)
      ctx = a.context.sorted.mkString(" || ")
    } yield ((normalizeChord(b), ctx), ConflictEntry(c.id, a.action))

    entries
      .groupBy(_._1)
      .toList
      .flatMap {
        case ((chord, ctx), pairs) =>
          val uniqueActions = pairs.map(_._2).distinctBy(_.action)
          if (uniqueActions.size > 1) {
            List(BindingConflict(chord, "zed", ctx, uniqueActions))
          } else {
            List.empty
          }
      }
  }

  private def findConflicts(
    entries: List[(Chord, ConflictEntry)],
    editor: String,
  ): List[BindingConflict] = {
    entries
      .groupBy(_._1)
      .toList
      .flatMap {
        case (chord, pairs) =>
          val uniqueActions = pairs.map(_._2).distinctBy(_.action)
          if (uniqueActions.size > 1) {
            List(BindingConflict(chord, editor, "", uniqueActions))
          } else {
            List.empty
          }
      }
  }

  private val modifierOrder: Map[Modifier, Int] = Map(
    Modifier.Ctrl -> 0,
    Modifier.Alt -> 1,
    Modifier.Shift -> 2,
    Modifier.Meta -> 3,
  )

  private implicit val modifierOrdering: Ordering[Modifier] =
    Ordering.by(modifierOrder)

  private def normalizeChord(chord: Chord): Chord = {
    Chord(chord.combos.map(c => c.copy(modifiers = c.modifiers.sorted)))
  }
}
