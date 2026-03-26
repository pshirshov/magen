package io.septimalmind.magen.model

case class Chord(combos: List[Key.KeyCombo])

sealed trait Modifier

object Modifier {
  case object Ctrl extends Modifier
  case object Alt extends Modifier
  case object Shift extends Modifier
  case object Meta extends Modifier
}

sealed trait Key

object Key {
  case class NamedKey(name: String) extends Key

  object NamedKey {
    def make(s: String): NamedKey = {
      if (s.startsWith("[Key") && s.endsWith("]")) {
        new NamedKey(s.substring(4, 5))
      } else if (s.startsWith("[") && s.endsWith("]")) {
        new NamedKey(s.substring(1, s.length - 1))
      } else {
        new NamedKey(s)
      }
    }
  }

  case class KeyCombo(modifiers: List[Modifier], key: NamedKey) {
    def dropMods: KeyCombo = this.copy(modifiers = List.empty)
  }
}
