package io.septimalmind.magen

object ZedRenderer extends Renderer {
  override def id: String = "zed.json"

  override def render(mapping: Mapping): String = ???

  private def format(binding: String): String = binding
}
