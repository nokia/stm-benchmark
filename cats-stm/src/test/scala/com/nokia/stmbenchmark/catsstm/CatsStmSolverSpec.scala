/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package catsstm

import scala.concurrent.duration._

import cats.effect.{ IO, Resource }

import common.JvmCeIoSolverSpec
import common.Solver

final class CatsStmSolverSpec extends JvmCeIoSolverSpec {

  final override def munitIOTimeout =
    180.minutes

  protected[this] final override def solverRes: Resource[IO, Solver[IO]] = {
    Resource.eval(IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      CatsStmSolver[IO](txnLimit = 2L * numCpu, parLimit = numCpu, log = false)
    })
  }

  testFromResource("four_crosses.txt".tag(Verbose))
  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt", restrict = 2) // unrestricted takes approx. 7 mins
  testFromResource("mainboard.txt", restrict = 5) // unrestricted takes approx. 1h 20m
  testFromResource("memboard.txt", restrict = 4) // unrestricted takes approx. 55 mins
}
