package io.septimalmind.magen.targets

import io.septimalmind.magen.model.Mapping

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

trait Installer {
  def install(mapping: Mapping): Unit

  protected def write(rendered: String, p: Path): Unit = {
    if (p.toFile.exists()) {
      p.toFile.delete()
    }
    Files.write(p, rendered.getBytes(StandardCharsets.UTF_8))
    ()
  }

}

case class IdeaParams(
  writeTo: List[Path],
  negate: Boolean,
  parent: String,
)

class IdeaInstaller(params: IdeaParams) extends Installer {
  def install(mapping: Mapping): Unit = {
    val rendered = new IdeaRenderer(params).render(mapping)
    params.writeTo.foreach {
      p =>
        write(rendered, p)
    }
  }

}

case class VscodeParams(
  writeTo: List[Path]
)

class VscodeInstaller(params: VscodeParams) extends Installer {
  def install(mapping: Mapping): Unit = {
    val rendered = VSCodeRenderer.render(mapping)
    params.writeTo.foreach {
      p =>
        write(rendered, p)
    }
  }
}

case class ZedParams(
  writeTo: List[Path]
)

class ZedInstaller(params: ZedParams) extends Installer {
  def install(mapping: Mapping): Unit = {
    val rendered = ZedRenderer.render(mapping)
    params.writeTo.foreach {
      p =>
        write(rendered, p)
    }
  }
}
