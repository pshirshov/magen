package io.septimalmind.magen.model

case class IdeaAction(action: String)
case class VSCodeAction(action: String, context: Option[List[String]])
case class ZedAction(action: String, context: Option[List[String]])

case class Impl(
  target: String,
  action: String,
  context: Option[List[String]],
)

case class Concept(
  id: String,
  binding: List[String],
  idea: Option[IdeaAction],
  vscode: Option[VSCodeAction],
  zed: Option[ZedAction],
)
