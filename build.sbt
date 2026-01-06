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
      "--initialize-at-build-time",
      "-H:+ReportExceptionStackTraces",
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
