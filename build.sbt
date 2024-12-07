/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

// Scala versions:
val scala2 = "2.13.15"
val scala3 = "3.5.2"

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
    common.jvm, common.js,
    benchmarks,
    sequential,
    catsStm,
    zstm,
    choam,
    scalaStm,
    arrowStm,
    kyoStm
  )

lazy val common = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("common"))
  .settings(name := "stm-benchmark-common")
  .settings(commonSettings)
  .jvmSettings(commonSettingsJvm)
  .jsSettings(commonSettingsJs)
  .settings(libraryDependencies ++= Seq(
    dependencies.fs2.value,
  ))

lazy val benchmarks = project.in(file("benchmarks"))
  .settings(name := "stm-benchmark-benchmarks")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(
    publishArtifact := false,
    Jmh / version := dependencies.jmhVersion,
    Jmh / bspEnabled := false, // https://github.com/sbt/sbt-jmh/issues/193
    libraryDependencies += dependencies.choamProfiler.value,
  )
  .dependsOn(sequential)
  .dependsOn(catsStm)
  .dependsOn(zstm)
  .dependsOn(choam)
  .dependsOn(scalaStm)
  .dependsOn(arrowStm)
  .dependsOn(kyoStm)
  .enablePlugins(JmhPlugin)

lazy val sequential = project.in(file("sequential"))
  .settings(name := "stm-benchmark-sequential")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
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

lazy val choam = project.in(file("choam"))
  .settings(name := "stm-benchmark-choam")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      dependencies.choam.value,
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
    kotlin.Keys.kotlinVersion := "2.1.0",
    kotlin.Keys.kotlincJvmTarget := "11",
  )

lazy val kyoStm = project.in(file("kyo-stm"))
  .settings(name := "stm-benchmark-kyo-stm")
  .settings(commonSettings)
  .settings(commonSettingsJvm)
  .settings(publishArtifact := false)
  .dependsOn(common.jvm % "compile->compile;test->test")
  .settings(
    crossScalaVersions := Seq(scala3),
    libraryDependencies ++= Seq(
      dependencies.kyoStm.value,
    ),
  )

lazy val commonSettingsJvm = Seq[Setting[_]](
  Test / fork := true,
)

lazy val commonSettingsJs = Seq[Setting[_]](
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
        "-Ykind-projector",
        "-Ysafe-init",
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
    """|© 2023-2024 Nokia
       |Licensed under the Apache License 2.0
       |SPDX-License-Identifier: Apache-2.0
       |""".stripMargin
  )),
)

lazy val dependencies = new {

  val catsVersion = "2.12.0"
  val catsEffectVersion = "3.5.7"
  val catsStmVersion = "0.13.4"
  val zioVersion = "2.1.13"
  val choamVersion = "0.4.8"
  val fs2Version = "3.11.0"
  val kindProjectorVersion = "0.13.3"
  val betterMonadicForVersion = "0.3.1"
  val jmhVersion = "1.37"

  val catsKernel = Def.setting("org.typelevel" %%% "cats-kernel" % catsVersion)
  val catsCore = Def.setting("org.typelevel" %%% "cats-core" % catsVersion)
  val catsFree = Def.setting("org.typelevel" %%% "cats-free" % catsVersion)
  val catsEffectKernel = Def.setting("org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion)
  val catsEffectStd = Def.setting("org.typelevel" %%% "cats-effect-std" % catsEffectVersion)
  val catsEffectAll = Def.setting("org.typelevel" %%% "cats-effect" % catsEffectVersion)
  val catsEffectTestkit = Def.setting("org.typelevel" %%% "cats-effect-testkit" % catsEffectVersion)
  val fs2 = Def.setting("co.fs2" %%% "fs2-io" % fs2Version)

  val test = Def.setting[Seq[ModuleID]] {
    Seq(
      catsEffectAll.value,
      "org.typelevel" %%% "cats-effect-kernel-testkit" % catsEffectVersion,
      catsEffectTestkit.value,
      "org.typelevel" %%% "munit-cats-effect" % "2.0.0",
    )
  }

  val catsStm = Def.setting("io.github.timwspence" %%% "cats-stm" % catsStmVersion)
  val choam = Def.setting("dev.tauri" %%% "choam-async" % choamVersion)
  val choamProfiler = Def.setting("dev.tauri" %%% "choam-profiler" % choamVersion)
  val scalaStm = Def.setting("org.scala-stm" %%% "scala-stm" % "0.11.1")
  val zioCats = Def.setting("dev.zio" %%% "zio-interop-cats" % "23.1.0.3")
  val zioStm = Def.setting("dev.zio" %%% "zio" % zioVersion)
  val kyoStm = Def.setting("io.getkyo" %%% "kyo-stm" % "0.15.1")

  val arrowStm = Def.setting("io.arrow-kt" % "arrow-fx-stm-jvm" % "2.0.0")
  val kotlinxCoroutines = Def.setting("org.jetbrains.kotlinx" % "kotlinx-coroutines-jdk8" % "1.9.0")
  val scalaJava8Compat = Def.setting("org.scala-lang.modules" %%% "scala-java8-compat" % "1.0.2")
}

addCommandAlias("staticAnalysis", ";headerCheckAll;Test/compile")
addCommandAlias("validate", ";staticAnalysis;test")

// profiling: `-prof jfr`
addCommandAlias("measurePerformance", "bench/jmh:run -foe true -rf json -rff results.json .*")
