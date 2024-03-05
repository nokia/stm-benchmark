/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package catsstm

import cats.effect.IO

import common.JvmCeIoSolverSpec
import common.Solver

final class CatsStmSolverSpec extends JvmCeIoSolverSpec {

  protected override def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      CatsStmSolver[IO](txnLimit = 2L * numCpu, parLimit = numCpu, log = false)
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_micro.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt".ignore) // very long (approx. 10 mins), but works
  testFromResource("mainboard.txt".ignore) // too long (more than 1 hour)
}
