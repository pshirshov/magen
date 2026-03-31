import scala.collection.immutable.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name := "magen",
    assembly / mainClass := Some("io.septimalmind.magen.Magen"),
    assembly / assemblyJarName := "magen.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", _*)            => MergeStrategy.first
      case "module-info.class"                 => MergeStrategy.discard
      case x if x.contains("$qmark") || x.contains("$tilde") || x.contains("$greater") || x.contains("$less") =>
        MergeStrategy.first
      case x => MergeStrategy.first
    },
    assembly / assemblyOption ~= { _.withIncludeScala(true) },
    Compile / mainClass := Some("io.septimalmind.magen.Magen"),
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      // Only initialize app and dependency packages at build time.
      // JDK classes (especially AWT) stay at default runtime init.
      "--initialize-at-build-time=scala,io.septimalmind,io.circe,cats,shapeless,izumi,org.snakeyaml,org.yaml,org.typelevel,jawn,macrocompat,io.github",
      "-H:+ReportExceptionStackTraces",
      "-H:+AddAllCharsets",
      "--enable-url-protocols=jar",
      // Exclude bundled resources from native image - they ship as a separate zip
      "--exclude-config", ".*", "META-INF/native-image/resource-config\\.json",
    ),
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.3.0",
    libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
    libraryDependencies += "io.circe" %% "circe-yaml-v12" % "0.16.0",
    libraryDependencies += "io.circe" %% "circe-generic" % "0.14.10",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.10",
    libraryDependencies ++= Seq(
      "fundamentals-platform",
      "fundamentals-functional",
      "fundamentals-language",
      "fundamentals-collections",
      "distage-core",
    ).map("io.7mind.izumi" %% _ % "1.2.16"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test,
    Test / parallelExecution := false,
    scalacOptions ++= Seq(
      "-Wconf:cat=other-match-analysis:error",
      "-encoding",
      "utf8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:experimental.macros",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-explaintypes",
      "-Xsource:3-cross",
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified",
      "-Wconf:msg=nowarn:silent",
      "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vtype-diffs",
    ),
  )

// Task to package bundled data resources into a zip file
lazy val packageDataZip = taskKey[File]("Package bundled data resources into magen-data.zip")
packageDataZip := {
  val resourceDir = (Compile / resourceDirectory).value
  val targetDir   = target.value
  val zipFile     = targetDir / "magen-data.zip"
  val log         = streams.value.log

  val dataDirs = Seq("mappings", "negations", "editor-mappings", "idea-keymaps")
  val dataFiles = Seq("idea-vscode-mapping.json")

  val entries = scala.collection.mutable.ListBuffer.empty[(java.io.File, String)]

  dataDirs.foreach { dir =>
    val base = resourceDir / dir
    if (base.exists()) {
      val files = (base ** "*").get.filter(_.isFile)
      files.foreach { f =>
        val relative = base.toPath.getParent.relativize(f.toPath).toString
        entries += ((f, relative))
      }
    }
  }

  dataFiles.foreach { name =>
    val f = resourceDir / name
    if (f.exists()) {
      entries += ((f, name))
    }
  }

  log.info(s"Packaging ${entries.size} data files into $zipFile")

  val zipOut = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zipFile))
  try {
    entries.foreach { case (file, entryName) =>
      zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName))
      val bytes = java.nio.file.Files.readAllBytes(file.toPath)
      zipOut.write(bytes)
      zipOut.closeEntry()
    }
  } finally {
    zipOut.close()
  }

  log.info(s"Created $zipFile")
  zipFile
}
