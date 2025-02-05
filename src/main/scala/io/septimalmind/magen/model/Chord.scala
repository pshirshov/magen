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
    def make(s: String) = {
      if (s.startsWith("[Key")) {
        new NamedKey(s.substring(4, 5))
      } else {
        new NamedKey(s)
      }
    }
  }
  case class KeyCombo(modifiers: List[Modifier], key: NamedKey) {
    def dropMods: KeyCombo = this.copy(modifiers = List.empty)
  }
}
