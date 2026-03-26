package io.septimalmind.magen

import io.septimalmind.magen.model.{Mapping, Platform}

trait Renderer {
  def id: String
  def render(mapping: Mapping, platform: Platform): String
}
