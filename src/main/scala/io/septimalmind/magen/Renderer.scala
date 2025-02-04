package io.septimalmind.magen

trait Renderer {
  def id: String
  def render(mapping: Mapping): String
}






