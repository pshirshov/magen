package io.septimalmind.magen

import io.septimalmind.magen.model.Mapping

trait Renderer {
  def id: String
  def render(mapping: Mapping): String
}






