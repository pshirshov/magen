package io.septimalmind.magen

import io.septimalmind.magen.Key.KeyCombo

object Aliases {
  def extend(binding: List[KeyCombo]): List[List[KeyCombo]] = {
    binding match {
      case f :: s :: Nil if f.modifiers == s.modifiers && f.modifiers.size == 1 =>
        val fs = f
        val ssShort = s.dropMods
        List(binding, List(fs, ssShort))

      case o =>
        List(o)
    }
  }

}
