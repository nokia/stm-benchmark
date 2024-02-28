/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import cats.effect.IO

import munit.Location

import common.JvmCeIoSolverSpec
import common.Solver

final class RxnSolverSpec extends JvmCeIoSolverSpec {

  override protected def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      RxnSolver[IO](parLimit = numCpu, log = false)
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt".ignore) // TODO: ?
}
