/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

val circeVersion = "0.14.14"
val kindProjectorVersion = "0.13.3"
val macroParadiseVersion = "2.1.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-generic-extras" % "0.14.4",
  "io.circe" %% "circe-parser" % circeVersion,
  compilerPlugin("org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full),
  compilerPlugin("org.scalamacros" % "paradise" % macroParadiseVersion cross CrossVersion.full),
)

ThisBuild / semanticdbEnabled := true

scalacOptions ++= Seq(
  s"-P:semanticdb:sourceroot:${(ThisBuild / baseDirectory).value.absolutePath}", // metals needs this
)
