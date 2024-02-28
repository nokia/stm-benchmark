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

  final override protected def assertTsk(cond: Boolean)(implicit loc: Location): IO[Unit] =
    IO { assert(cond) }

  override protected def debug(msg: String): IO[Unit] =
    IO.consoleForIO.println(msg)

  override protected def munitValueTransform: Option[ValueTransform] =
    None

  override protected def createSolver: IO[Solver[IO]] = {
    IO { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      RxnSolver[IO](parLimit = numCpu, log = false)
    }
  }

  testFromResource("testBoard.txt", printSolution = true)
  testFromResource("sparseshort.txt")
  testFromResource("sparselong.txt")
  // TODO: mainboard.txt (?)
}
