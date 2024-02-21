/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import scala.concurrent.duration._

import cats.syntax.all._
import cats.effect.Async

import fs2.Stream

import munit.{ CatsEffectSuite, Location }

abstract class AbstractSolverSpec extends CatsEffectSuite {

  type Tsk[a]

  protected def createSolver: Tsk[Solver[Tsk]]

  protected def debug(msg: String): Tsk[Unit]

  protected def assertTsk(cond: Boolean)(implicit loc: Location): Tsk[Unit]

  protected def munitValueTransform: Option[ValueTransform]

  protected implicit def asyncInstance: Async[Tsk]

  final override def munitValueTransforms: List[ValueTransform] =
    this.munitValueTransform.toList ++ super.munitValueTransforms

  final override def munitIOTimeout =
    120.minutes

  protected def printAndCheckSolution(board: Board, solution: Solver.Solution)(implicit loc: Location): Tsk[Unit] =
    debug(board.debugSolution(solution.value)) *> assertTsk(board.isSolutionValid(solution.value))

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/minimal.txt
  test("minimal") {
    createSolver.flatMap { solver =>
      val s = Stream[Tsk, String](
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
