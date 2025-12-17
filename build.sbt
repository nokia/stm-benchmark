/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

// Scala versions:
val scala2 = "2.13.17"
val scala3 = "3.7.4"

val TestInternal = "test-internal"

ThisBuild / crossScalaVersions := Seq(scala3, scala2)
ThisBuild / scalaVersion := crossScalaVersions.value.head
ThisBuild / scalaOrganization := "org.scala-lang"
ThisBuild / evictionErrorLevel := Level.Warn
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val stmBenchmark = project.in(file("."))
  .settings(name := "stm-benchmark")
  .settings(commonSettings)
  .settings(publishArtifact := false)
  .aggregate(
    common.jvm, common.js, common.native,
    benchmarks,
    sequential.jvm, sequential.native,
    catsStm,
    zstm,
    choam.jvm, choam.native,
    scalaStm,
    arrowStm,
    kyoStm
  )

lazy val common = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("common"))
  .settings(name := "stm-benchmark-common")
  .settings(commonSettings)
  .jvmSettings(commonSettingsJvm)
  .jsSettings(commonSettingsJs)
  .nativeSettings(commonSettingsNative)
  .settings(libraryDependencies ++= Seq(
    dependencies.fs2.value,
    dependencies.catsEffectStd.value,
    dependencies.catsCore.value,
    dependencies.catsEffectTestkit.value % TestInternal,
  ))

lazy val benchmarks = project.in(file("benchmarks"))
  .settings(name := "stm-benchmark-benchmarks")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(
    publishArtifact := false,
    Jmh / version := dependencies.jmhVersion,
    Jmh / bspEnabled := false, // https://github.com/sbt/sbt-jmh/issues/193
    libraryDependencies ++= Seq(
      dependencies.choamProfiler.value,
      dependencies.zioCats.value,
      dependencies.catsEffectAll.value,
    ),
  )
  .dependsOn(sequential.jvm)
  .dependsOn(catsStm)
  .dependsOn(zstm)
  .dependsOn(choam.jvm)
  .dependsOn(scalaStm)
  .dependsOn(arrowStm)
  .dependsOn(kyoStm)
  .enablePlugins(JmhPlugin)

lazy val sequential = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("sequential"))
  .settings(name := "stm-benchmark-sequential")
  .settings(commonSettings)
  .jvmSettings(commonSettingsJvm)
  .nativeSettings(commonSettingsNative)
  .settings(publishArtifact := false)
  .dependsOn(common % "compile->compile;test->test")
  .settings(libraryDependencies ++= Seq(
    dependencies.catsCore.value,
  ))

lazy val catsStm = project.in(file("cats-stm"))
  .settings(name := "stm-benchmark-cats-stm")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .settings(libraryDependencies ++= Seq(
    dependencies.catsStm.value,
  ))

lazy val zstm = project.in(file("zstm"))
  .settings(name := "stm-benchmark-zstm")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .settings(libraryDependencies ++= Seq(
    dependencies.zioStm.value,
    dependencies.zioCats.value % Test,
  ))

lazy val choam = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("choam"))
  .settings(name := "stm-benchmark-choam")
  .settings(commonSettings)
  .jvmSettings(commonSettingsJvm)
  .nativeSettings(commonSettingsNative)
  .settings(publishArtifact := false)
  .dependsOn(common % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      dependencies.choam.value,
      dependencies.catsEffectStd.value,
    ),
    Test / javaOptions += "-Ddev.tauri.choam.stats=true",
  )

lazy val scalaStm = project.in(file("scala-stm"))
  .settings(name := "stm-benchmark-scala-stm")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .settings(libraryDependencies ++= Seq(
    dependencies.scalaStm.value,
    dependencies.catsFree.value,
  ))

lazy val arrowStm = project.in(file("arrow-stm"))
  .settings(name := "stm-benchmark-arrow-stm")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .enablePlugins(KotlinPlugin)
  .settings(
    libraryDependencies ++= Seq(
      dependencies.arrowStm.value,
      dependencies.kotlinxCoroutines.value,
      dependencies.scalaJava8Compat.value,
    ),
    kotlin.Keys.kotlinLib("stdlib"),
    kotlin.Keys.kotlinVersion := "2.1.20",
    kotlin.Keys.kotlincJvmTarget := "11",
  )

lazy val kyoStm = project.in(file("kyo-stm"))
  .settings(name := "stm-benchmark-kyo-stm")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= (
      if (ScalaArtifacts.isScala3(scalaVersion.value)) {
        Seq(
          dependencies.kyoStm.value,
          dependencies.kyoCats.value % TestInternal,
        )
      } else {
        Nil
      }
    ),
    // Test / javaOptions += "-Dkyo.scheduler.enableTopConsoleMs=10000",
  )

lazy val commonSettingsJvm = Seq[Setting[_]](
  Test / fork := true,
  // Test / javaOptions += "-XX:ActiveProcessorCount=2",
)

lazy val commonSettingsJs = Seq[Setting[_]](
)

lazy val commonSettingsNative = Seq[Setting[_]](
  nativeConfig ~= { config =>
    config
      .withMultithreading(true)
      .withSourceLevelDebuggingConfig(_.enableAll)
      .withCompileOptions(_ ++ Seq(
        "-DGC_ASSERTIONS",
      ))
      .withEmbedResources(true)
      .withResourceIncludePatterns(Seq("**.txt")) // board files
  },
  envVars ++= Map(
    "GC_MAXIMUM_HEAP_SIZE" -> "8G",
    "SCALANATIVE_TEST_DEBUG_SIGNALS" -> "1",
  ),
)

lazy val commonSettings = Seq[Setting[_]](
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-encoding", "UTF-8",
    "-language:higherKinds,experimental.macros",
    "-release", "11",
    "-Xmigration:2.13.13",
  ),
  scalacOptions ++= (
    if (!ScalaArtifacts.isScala3(scalaVersion.value)) {
      // 2.13:
      List(
        // -release implies -target
        "-Xsource:3-cross",
        "-Xverify",
        "-Wconf:any:warning-verbose",
        "-Ywarn-unused:implicits",
        "-Ywarn-unused:imports",
        "-Ywarn-unused:locals",
        "-Ywarn-unused:patvars",
        "-Ywarn-unused:params",
        "-Ywarn-unused:privates",
        // no equivalent:
        "-opt:l:inline",
        "-opt-inline-from:<sources>",
        "-Xlint:_",
        "-Ywarn-numeric-widen",
        "-Ywarn-dead-code",
        "-Ywarn-value-discard",
        s"-P:semanticdb:sourceroot:${(ThisBuild / baseDirectory).value.absolutePath}", // metals needs this
      )
    } else {
      // 3.x:
      List(
        // -release implies -Xtarget
        "-source:3.3",
        "-Xverify-signatures",
        "-Wunused:all",
        // no equivalent:
        "-Xkind-projector",
        "-Wsafe-init",
        "-Ycheck-all-patmat",
      )
    }
  ),
  Compile / console / scalacOptions ~= { _.filterNot("-Ywarn-unused-import" == _).filterNot("-Ywarn-unused:imports" == _) },
  Test / console / scalacOptions := (Compile / console / scalacOptions).value,
  javacOptions ++= Seq(
    "--release", "11", // implies "-source 11 -target 11"
    "-Xlint",
  ),
  // Somewhat counter-intuitively, to really run
  // tests sequentially, we need to set this to true:
  Test / parallelExecution := true,
  // And then add this restriction:
  concurrentRestrictions += Tags.limit(Tags.Test, 1),
  // (Otherwise when running `test`, the different
  // subprojects' tests still run concurrently; see
  // https://github.com/sbt/sbt/issues/2516 and
  // https://github.com/sbt/sbt/issues/2425.)
  libraryDependencies ++= Seq(
    dependencies.test.value.map(_ % TestInternal)
  ).flatten,
  libraryDependencies ++= (
    if (!ScalaArtifacts.isScala3(scalaVersion.value)) {
      List(
        compilerPlugin("org.typelevel" % "kind-projector" % dependencies.kindProjectorVersion cross CrossVersion.full),
        compilerPlugin("com.olegpy" %% "better-monadic-for" % dependencies.betterMonadicForVersion),
      )
    } else {
      Nil
    }
  ),
  // bspEnabled := crossProjectPlatform.?.value.forall(_ == JVMPlatform),
  organization := "com.nokia",
  version := "0.0.0",
  publishMavenStyle := true,
  publishArtifact := false, // TODO
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerLicense := Some(HeaderLicense.Custom(
    """|© 2023-2025 Nokia
       |Licensed under the Apache License 2.0
       |SPDX-License-Identifier: Apache-2.0
       |""".stripMargin
  )),
)

lazy val dependencies = new {

  val catsVersion = "2.13.0"
  val catsEffectVersion = "3.7.0-RC1"
  val catsStmVersion = "0.13.5"
  val zioVersion = "2.1.22"
  val kyoVersion = "1.0-RC1"
  val choamVersion = "0.5-77f48d0"
  val fs2Version = "3.13.0-M7"
  val kindProjectorVersion = "0.13.3"
  val betterMonadicForVersion = "0.3.1"
  val jmhVersion = "1.37"

  val catsCore = Def.setting("org.typelevel" %%% "cats-core" % catsVersion)
  val catsFree = Def.setting("org.typelevel" %%% "cats-free" % catsVersion)
  val catsEffectStd = Def.setting("org.typelevel" %%% "cats-effect-std" % catsEffectVersion)
  val catsEffectAll = Def.setting("org.typelevel" %%% "cats-effect" % catsEffectVersion)
  val catsEffectTestkit = Def.setting("org.typelevel" %%% "cats-effect-testkit" % catsEffectVersion)
  val fs2 = Def.setting("co.fs2" %%% "fs2-io" % fs2Version)

  val test = Def.setting[Seq[ModuleID]] {
    Seq(
      catsEffectAll.value,
      "org.typelevel" %%% "cats-effect-kernel-testkit" % catsEffectVersion,
      catsEffectTestkit.value,
      "org.scalameta" %%% "munit" % "1.2.1",
      "org.typelevel" %%% "munit-cats-effect" % "2.2.0-RC1",
    )
  }

  val catsStm = Def.setting("io.github.timwspence" %%% "cats-stm" % catsStmVersion)
  val choam = Def.setting("dev.tauri" %%% "choam-async" % choamVersion)
  val choamProfiler = Def.setting("dev.tauri" %%% "choam-profiler" % choamVersion)
  val scalaStm = Def.setting("org.scala-stm" %%% "scala-stm" % "0.11.1")
  val zioCats = Def.setting("dev.zio" %%% "zio-interop-cats" % "23.1.0.5")
  val zioStm = Def.setting("dev.zio" %%% "zio" % zioVersion)
  val kyoStm = Def.setting("io.getkyo" %%% "kyo-stm" % kyoVersion)
  val kyoCats = Def.setting("io.getkyo" %%% "kyo-cats" % kyoVersion)

  val arrowStm = Def.setting("io.arrow-kt" % "arrow-fx-stm-jvm" % "2.1.2")
  val kotlinxCoroutines = Def.setting("org.jetbrains.kotlinx" % "kotlinx-coroutines-jdk8" % "1.10.2")
  val scalaJava8Compat = Def.setting("org.scala-lang.modules" %%% "scala-java8-compat" % "1.0.2")
}

addCommandAlias("staticAnalysis", ";headerCheckAll;Test/compile")
addCommandAlias("validate", ";staticAnalysis;test")

// We always want some JMH arguments, so we define an alias for them:
addCommandAlias("runBenchmarks", "benchmarks/Jmh/run -foe true -rf json -rff results/jmh-result.json")

// For big boards, we want to run JMH in single-shot mode:
addCommandAlias("runLongBenchmarks", "benchmarks/Jmh/run -foe true -rf json -rff results/jmh-result.json -bm ss -to 3hr")

// Other common JMH arguments:
// `-p board=mainboard.txt` (specifying parameters)
// `-prof jfr` (configuring profilers)
// `-jvmArgsAppend -XX:ActiveProcessorCount=2` (constraining cores used by the forked benchmarking JVMs)

// Running benchmarks on various number of CPU cores:
import sbt._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

addCommandAlias("runBenchmarksNCPU", "internalRunInternalNCPU short")
addCommandAlias("runLongBenchmarksNCPU", "internalRunInternalNCPU long")

lazy val internalRunInternalNCPU = inputKey[Unit]("used by runBenchmarksNCPU and runLongBenchmarksNCPU")
internalRunInternalNCPU := Def.inputTaskDyn {

  // parse:
  // - whitespace
  // - short|long
  // - whitespace
  // - comma separated list of integers
  // - usual (additional) JMH arguments
  val args: ((String, Seq[Int]), Option[Seq[String]]) = (Space ~> ((literal("short") | literal("long")) <~ Space) ~ rep1sep(IntBasic, ",") ~ (spaceDelimited("<arg>")).?).parsed
  val (runLong, ncpuLst, addArgs) = args match {
    case (("short", lst), addArgs) => (false, lst, addArgs.getOrElse(Seq.empty))
    case (("long", lst), addArgs) => (true, lst, addArgs.getOrElse(Seq.empty))
    case x => throw new IllegalArgumentException(x.toString)
  }
  val dryRun = addArgs.contains("-l") || addArgs.contains("-lp")
  val resultFilePattern: Int => String = if (!runLong) {
    { ncpu => s"results/jmh-result-ncpu_${ncpu}.json" }
  } else {
    { ncpu => s"results/jmh-result-long-ncpu_${ncpu}.json" }
  }
  val outFiles = ncpuLst.map(resultFilePattern)

  Def.sequential(
    ncpuLst.map { ncpu =>
      val resultFile = resultFilePattern(ncpu)
      Def.taskDyn {
        val additionalArgs = " " + addArgs.mkString(" ")
        val jmhArgs = if (!runLong) {
          s" -foe true -rf json -rff ${resultFile} -jvmArgsAppend -XX:ActiveProcessorCount=${ncpu}${additionalArgs}"
        } else {
          s" -foe true -rf json -rff ${resultFile} -bm ss -to 3hr -jvmArgsAppend -XX:ActiveProcessorCount=${ncpu}${additionalArgs}"
        }
        (benchmarks / Jmh / run).toTask(jmhArgs)
      }
    } ++ (if (dryRun) {
      Nil
    } else {
      Def.task {
        MergeBenchResults.mergeBenchResultsInternal(
          (benchmarks / baseDirectory).value,
          (benchmarks / streams).value.log,
          if (!runLong) "results/jmh-result.json" else "results/jmh-result-long.json",
          outFiles.toList,
          ncpuLst.toList,
        )
      } :: Nil
    })
  )
}.evaluated
