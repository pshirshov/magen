package io.septimalmind.magen

import io.septimalmind.magen.Key.KeyCombo

trait Renderer {
  def id: String
  def render(mapping: Mapping): String
}

object VSCodeRenderer extends Renderer {
  override def id: String = "vscode.json"

  override def render(mapping: Mapping): String = ???

  private def format(binding: String): String = binding
}

object ZedRenderer extends Renderer {
  override def id: String = "zed.json"

  override def render(mapping: Mapping): String = ???

  private def format(binding: String): String = binding
}


