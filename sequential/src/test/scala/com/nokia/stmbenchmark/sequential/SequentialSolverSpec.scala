/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package sequential

import cats.effect.IO

import common.JvmCeIoSolverSpec
import common.Solver

import munit.Location

final class SequentialSolverSpec extends JvmCeIoSolverSpec {

  protected override def createSolver: IO[Solver[IO]] =
    SequentialSolver[IO](log = false)

  protected override def debug(msg: String): IO[Unit] =
    IO.consoleForIO.println(msg)

  protected override def munitValueTransform: Option[ValueTransform] =
    None

  protected override def assertTsk(cond: Boolean)(implicit loc: Location): IO[Unit] =
    IO { assert(cond) }

  testFromResource("testBoard.txt", printSolution = true)
  testFromResource("sparseshort.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt")
}
