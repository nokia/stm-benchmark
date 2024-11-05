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
        parLimit = 1, // TODO: numCpu
        log = true, // TODO: false
      )
    }
  }
}
