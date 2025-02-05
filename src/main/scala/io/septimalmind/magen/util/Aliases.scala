package io.septimalmind.magen.util

import io.septimalmind.magen.model.Chord

object Aliases {
  def extend(binding: Chord): List[Chord] = {
    binding.combos match {
      case f :: s :: Nil if f.modifiers == s.modifiers && f.modifiers.size == 1 =>
        val fs = f
        val ssShort = s.dropMods
        List(binding, Chord(List(fs, ssShort)))

      case _ =>
        List(binding)
    }
  }

}
