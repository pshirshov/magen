package io.septimalmind.magen.model

import io.circe.Json
import izumi.fundamentals.collections.nonempty.NEList

case class RawIdeaAction(
  action: Option[String],
  mouse: Option[List[String]],
  missing: Option[Boolean],
)
case class RawVSCodeAction(
  action: Option[String],
  args: Option[Json],
  context: Option[List[String]],
  binding: Option[List[String]],
  missing: Option[Boolean],
)
case class RawZedAction(
  action: Option[String],
  args: Option[Json],
  context: Option[List[String]],
  missing: Option[Boolean],
)

case class RawConcept(
  id: String,
  binding: PlatformBinding,
  idea: Option[RawIdeaAction],
  vscode: Option[RawVSCodeAction],
  zed: Option[RawZedAction],
  unset: Option[Boolean],
)

case class RawMapping(
  keys: Option[Map[String, String]],
  mapping: Option[List[RawConcept]],
)

case class IdeaAction(
  action: String,
  mouse: List[String],
)

case class VSCodeAction(
  action: String,
  args: Option[Json],
  context: List[String],
  binding: List[Chord],
)
case class ZedAction(
  action: String,
  args: Option[Json],
  context: List[String],
)

case class Concept(
  id: String,
  binding: NEList[Chord],
  idea: Option[IdeaAction],
  vscode: Option[VSCodeAction],
  zed: Option[ZedAction],
)
case class Mapping(mapping: List[Concept])
