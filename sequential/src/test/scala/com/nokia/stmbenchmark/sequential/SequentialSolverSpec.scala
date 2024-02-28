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

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt")
}
