/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import scala.concurrent.duration._

import cats.effect.IO

import fs2.Stream

import munit.{ CatsEffectSuite, Location }

abstract class CeIoSolverSpec extends CatsEffectSuite {

  protected def createSolver: IO[Solver[IO]]

  protected def debug(msg: String): IO[Unit]

  protected def assertTsk(cond: Boolean)(implicit loc: Location): IO[Unit]

  protected def munitValueTransform: Option[ValueTransform]

  final override def munitValueTransforms: List[ValueTransform] =
    this.munitValueTransform.toList ++ super.munitValueTransforms

  final override def munitIOTimeout =
    120.minutes

  protected def checkSolution(board: Board, solution: Solver.Solution)(implicit loc: Location): IO[Unit] =
    assertTsk(board.isSolutionValid(solution.value))

  protected def printAndCheckSolution(board: Board, solution: Solver.Solution)(implicit loc: Location): IO[Unit] =
    debug(board.debugSolution(solution.value)) *> checkSolution(board, solution)

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/minimal.txt
  test("minimal") {
    createSolver.flatMap { solver =>
      val s = Stream[IO, String](
        List(
          "B 10 10",
          "P 2 2",
          "P 7 2",
          "P 2 7",
          "P 7 7",
          "J 2 2 7 7",
          "J 7 2 2 7",
          "E",
          "",
        ).mkString("\n")
      )
      Board.fromStream(s).flatMap { board =>
        solver.solve(board.normalize).flatMap { solution =>
          printAndCheckSolution(board, solution)
        }
      }
    }
  }
}
