/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package scalastm

import cats.effect.IO

import common.JvmCeIoSolverSpec
import common.Solver

final class WrStmSolverSpec extends JvmCeIoSolverSpec {

  protected override def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      WrStmSolver[IO](parLimit = numCpu, log = false)
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_micro.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt")
  testFromResource("memboard.txt")
}
