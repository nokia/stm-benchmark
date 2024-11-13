/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import cats.effect.IO
import cats.effect.unsafe.IORuntime

import common.Solver
import common.JvmCeIoSolverSpec

final class ArrowStmSolverSpec extends JvmCeIoSolverSpec with KotlinInterop { interop =>

  // Avoid creating a separate CE IORuntime, instead
  // use the threadpool(s) the coroutines will use:
  final override lazy val munitIORuntime: IORuntime =
    interop.ioRuntimeFromCoroutineDispatchers()

  protected final override def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      ArrowStmSolver(
        parLimit = numCpu,
        log = false,
      )
    }
  }

  testFromResource("four_crosses.txt".tag(Verbose))
  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt", restrict = 2) // unrestricted takes almost 6 minutes
  testFromResource("memboard.txt", restrict = 1) // unrestricted takes almost 4 minutes
}
