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

abstract class CeIoSolverSpec extends CatsEffectSuite with MunitUtils {

  protected def createSolver: IO[Solver[IO]]

  protected final def debug(msg: String): IO[Unit] =
    IO.consoleForIO.println(msg)

  protected final def assertTsk(cond: Boolean)(implicit loc: Location): IO[Unit] =
    IO(assert(cond)(loc))

  final override def munitIOTimeout =
    60.minutes

  protected def checkSolution(name: String, board: Board, solution: Solver.Solution)(implicit loc: Location): IO[Unit] = {
    debug(name + "\n" + Board.debugSolutionStats(solution, debug = true, indent = "  ")) *> (
      assertTsk(board.isSolutionValid(solution.routes))
    )
  }

  protected def printAndCheckSolution(name: String, board: Board, solution: Solver.Solution)(implicit loc: Location): IO[Unit] = {
    debug(board.debugSolution(solution.routes, debug = true)) *> (
      checkSolution(name, board, solution)
    )
  }

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
        solver.solve(board.normalize(42L)).flatMap { solution =>
          printAndCheckSolution("minimal.txt", board, solution)
        }
      }
    }
  }
}
