/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

ThisBuild / semanticdbEnabled := true

scalacOptions ++= Seq(
  s"-P:semanticdb:sourceroot:${(ThisBuild / baseDirectory).value.absolutePath}", // metals needs this
)
