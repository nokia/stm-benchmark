/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import cats.effect.IO

import common.Solver
import common.JvmCeIoSolverSpec

final class ArrowStmSolverSpec extends JvmCeIoSolverSpec {

  protected final override def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      ArrowStmSolver(
        parLimit = numCpu,
        log = false,
      )
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt")
}
