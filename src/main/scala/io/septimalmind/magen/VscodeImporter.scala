package io.septimalmind.magen

import io.circe.Json
import izumi.fundamentals.platform.files.IzFiles

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object VscodeImporter {
  case class VscB(key: String, context: Option[String])
  def main(args: Array[String]): Unit = {
    import izumi.fundamentals.collections.IzCollections.*
    val index = io.circe.parser.parse(IzFiles.readString(Paths.get("./junk/vscode.json"))).toOption.get

    val contexts = index.asArray.get
      .map(_.asObject.get.toMap)
      .map {
        o =>
          val a = o("command").asString.get
          val k = o("key").asString.get
          val c = o.get("when").flatMap(_.asString) // , Json.fromString("true")).asString.get
          a -> VscB(k, c)
      }
      .toMultimap

    val processed = scala.collection.mutable.HashSet.empty[String]
    val sb = new StringBuilder()
    sb.append("mapping:\n");
    contexts
      .filterNot(_._1.startsWith("-"))
      .foreach {
        case (cmd, bbs) =>
          val allbbs = bbs.map(_.key).toList
          
          val secs = allbbs.flatMap {
            b =>
              val parsed = ShortcutParser.parseUnsafe(b)
              val extended = Aliases.extend(parsed)
              
              val all = extended.map(_.map(VSCodeRenderer.renderCombo).mkString(" ")).sortBy(_.length)
              val secs = all.init
              secs
          }.toSet
          
          val filtered = allbbs.filterNot {
            b =>
              val parsed = ShortcutParser.parseUnsafe(b)
              secs.contains(parsed.map(VSCodeRenderer.renderCombo).mkString(" "))
          }
          
          import izumi.fundamentals.platform.strings.IzString.*
          
          val b = filtered.map(s => s"\"${s}\"").niceList().shift(5) //if (allbbs.size > 1) s"'${allbbs.head}' # ${allbbs.tail.mkString("; ")}" else s"'${allbbs.head}'"
          val ccs = bbs.map(_.context)
          val ccsString = ccs.flatten.map(s => s"\"$s\"").mkString("[ ", ", ", " ]").replace("\\", "\\\\")

          if (ccs.exists(_.nonEmpty)) {
            if (ccs.exists(_.isEmpty)) {
              sb.append(s"""  - id: "$cmd"
                           |    binding: $b
                           |    vscode:
                           |      action: '$cmd'
                           |      # context: $ccsString""".stripMargin)
            } else {
              sb.append(s"""  - id: "$cmd"
                           |    binding: $b
                           |    vscode:
                           |      action: '$cmd'
                           |      context: $ccsString""".stripMargin)
            }
          } else {
            sb.append(s"""  - id: "$cmd"
                         |    binding: $b
                         |    vscode:
                         |      action: '$cmd'""".stripMargin)
          }
          IdeaRenderer.basicMappings.get(cmd) match {
            case Some(value) =>
              processed.add(cmd)
              sb.append('\n');
              sb.append(s"""    idea:
                           |      action: '$value'""".stripMargin)

            case None =>
          }
          sb.append('\n');
      }

    IdeaRenderer.basicMappings
      .filterNot(a => processed.contains(a._1))
      .foreach {
      case (k, v) =>
//        val w = contexts.get(k).toSeq.flatten.map(s => s"\"$s\"")
        val ccs = contexts.get(k).map(_.flatMap(_.context)).getOrElse(Set.empty)
        
        val ccsString = ccs.flatten.map(s => s"\"$s\"").mkString("[ ", ", ", " ]")

        val b = "'unset'"
        if (ccs.exists(_.nonEmpty)) {
          if (ccs.exists(_.isEmpty)) {
            sb.append(s"""  - id: "$k"
                         |    binding: 
                         |      - ${b}
                         |    vscode:
                         |      action: '$k'
                         |      # context: $ccsString""".stripMargin)
          } else {
            sb.append(s"""  - id: "$k"
                         |    binding: 
                         |      - ${b}
                         |    vscode:
                         |      action: '$k'
                         |      context: $ccsString""".stripMargin)
          }
        } else {
          sb.append(s"""  - id: "$k"
                       |    binding: 
                       |      - ${b}
                       |    vscode:
                       |      action: '$k'""".stripMargin)
        }
        sb.append('\n');
        sb.append(s"""    idea:
                     |      action: '$v'""".stripMargin)
      sb.append('\n');
    }

    Files.write(Paths.get("mappings", "vscode-idea-imported.yaml"), sb.toString().getBytes(StandardCharsets.UTF_8))
  }
}
