package io.septimalmind.magen.targets

import io.septimalmind.magen.model.{Mapping, Platform}
import io.septimalmind.magen.util.PathExpander

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

trait Installer {
  def install(mapping: Mapping): Unit

  protected def write(rendered: String, p: Path): Unit = {
    if (p.toFile.exists()) {
      p.toFile.delete()
    }
    Files.write(p, rendered.getBytes(StandardCharsets.US_ASCII))
    ()
  }

}

case class IdeaParams(
  writeToPatterns: List[String],
  negate: Boolean,
  parent: String,
  keymapName: String,
)

class IdeaInstaller(params: IdeaParams, platform: Platform) extends Installer {
  def install(mapping: Mapping): Unit = {
    val rendered = new IdeaRenderer(params).render(mapping, platform)
    val paths = PathExpander.expandGlobs(params.writeToPatterns)
    paths.foreach {
      p =>
        println(s"Installing into $p")
        write(rendered, p)
    }
  }

}

case class VscodeParams(
  writeToPatterns: List[String]
)

class VscodeInstaller(params: VscodeParams, platform: Platform) extends Installer {
  def install(mapping: Mapping): Unit = {
    val rendered = VSCodeRenderer.render(mapping, platform)
    val paths = PathExpander.expandGlobs(params.writeToPatterns)
    paths.foreach {
      p =>
        println(s"Installing into $p")
        write(rendered, p)
    }
  }
}

case class ZedParams(
  writeToPatterns: List[String]
)

class ZedInstaller(params: ZedParams, platform: Platform) extends Installer {
  def install(mapping: Mapping): Unit = {
    val rendered = ZedRenderer.render(mapping, platform)
    val paths = PathExpander.expandGlobs(params.writeToPatterns)
    paths.foreach {
      p =>
        println(s"Installing into $p")
        write(rendered, p)
    }
  }
}
