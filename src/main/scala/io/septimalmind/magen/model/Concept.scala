package io.septimalmind.magen.model

import izumi.fundamentals.collections.nonempty.NEList

case class RawIdeaAction(
  action: Option[String],
  mouse: Option[List[String]],
  missing: Option[Boolean],
)
case class RawVSCodeAction(
  action: Option[String],
  context: Option[List[String]],
  binding: Option[List[String]],
  missing: Option[Boolean],
)
case class RawZedAction(
  action: Option[String],
  context: Option[List[String]],
  missing: Option[Boolean],
)

case class RawConcept(
  id: String,
  binding: List[String],
  idea: Option[RawIdeaAction],
  vscode: Option[RawVSCodeAction],
  zed: Option[RawZedAction],
  unset: Option[Boolean],
)

case class RawMapping(mapping: List[RawConcept])

case class IdeaAction(
  action: String,
  mouse: List[String],
)

case class VSCodeAction(
  action: String,
  context: List[String],
  binding: List[String],
)
case class ZedAction(
  action: String,
  context: List[String],
)

case class Concept(
  id: String,
  binding: NEList[String],
  idea: Option[IdeaAction],
  vscode: Option[VSCodeAction],
  zed: Option[ZedAction],
)
case class Mapping(mapping: List[Concept])
