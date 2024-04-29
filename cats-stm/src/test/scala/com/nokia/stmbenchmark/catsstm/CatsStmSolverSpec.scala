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
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt", restrict = 2) // unrestricted takes approx. 10 mins
  testFromResource("mainboard.txt", restrict = 5) // unrestricted takes too long (more than 1 hour)
  testFromResource("memboard.txt", restrict = 4) // unrestricted takes too long (more than 1 hour)
}
