/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package sequential

import cats.effect.IO
import cats.effect.kernel.Async

import common.JvmSolverSpec
import common.Solver

import munit.Location

final class SequentialSolverSpec extends JvmSolverSpec {

  final override type Tsk[a] = IO[a]

  protected override implicit def asyncInstance: Async[IO] =
    IO.asyncForIO

  protected override def createSolver: IO[Solver[IO]] =
    SequentialSolver[IO](log = false)

  protected override def debug(msg: String): IO[Unit] =
    IO.consoleForIO.println(msg)

  protected override def munitValueTransform: Option[ValueTransform] =
    None

  protected override def assertTsk(cond: Boolean)(implicit loc: Location): IO[Unit] =
    IO { assert(cond) }
}
