/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package sequential

import cats.effect.{ IO, Resource }

import common.JvmCeIoSolverSpec
import common.Solver

final class SequentialSolverSpec extends JvmCeIoSolverSpec {

  protected[this] final override def solverRes: Resource[IO, Solver[IO]] =
    Resource.eval(SequentialSolver[IO](log = false))

  testFromResource("four_crosses.txt".tag(Verbose))
  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt", restrict = if (isJvm) 1 else 2)
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt", restrict = if (isJvm) 0 else 1)

  if (isJvm) { // TODO: SN: too big boards(?)
    testFromResource("mainboard.txt", restrict = 2)
    testFromResource("memboard.txt", restrict = 2)
  }
}
