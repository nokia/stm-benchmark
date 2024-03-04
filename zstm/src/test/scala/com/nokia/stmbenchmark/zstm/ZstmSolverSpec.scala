/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package zstm

import scala.concurrent.duration._

import zio.{ Task, ZIO }
import zio.interop.catz.asyncInstance

import fs2.Stream

import munit.{ Location, TestOptions }
import munit.ZSuite

import common.{ Board, Solver }
import common.MunitUtils

final class ZstmSolverSpec extends ZSuite with MunitUtils {

  final override def munitTimeout =
    60.minutes

  private def createSolver: Task[Solver[Task]] = {
    ZIO.attempt { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      ZstmSolver(parLimit = numCpu, log = false)
    }
  }

  private def debug(msg: String): Task[Unit] = {
    ZIO.consoleWith { console =>
      console.printLine(msg)
    }
  }

  protected def checkSolution(board: Board, solution: Solver.Solution)(implicit loc: Location): Task[Unit] =
    ZIO.attempt { assert(board.isSolutionValid(solution.value))(loc) }

  protected def printAndCheckSolution(board: Board, solution: Solver.Solution)(implicit loc: Location): Task[Unit] =
    debug(board.debugSolution(solution.value, debug = true)) *> checkSolution(board, solution)

  private def testFromResource(resourceName: String)(implicit loc: Location): Unit = {
    testFromResource(resourceNameAndOpts = resourceName)(loc)
  }

  private def testFromResource(resourceNameAndOpts: TestOptions)(implicit loc: Location): Unit = {
    testZ(resourceNameAndOpts) {
      createSolver.flatMap { solver =>
        Board.fromResource[Task](resourceNameAndOpts.name).flatMap { board =>
          solver.solve(board.normalize()).flatMap { solution =>
            if (resourceNameAndOpts.tags.contains(Verbose)) {
              printAndCheckSolution(board, solution)
            } else {
              checkSolution(board, solution)
            }
          }
        }
      }
    }
  }

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/minimal.txt
  testZ("minimal") {
    createSolver.flatMap { solver =>
      val s = Stream[Task, String](
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
          printAndCheckSolution(board, solution)
        }
      }
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt".ignore) // too long
  testFromResource("mainboard.txt".ignore) // too long (more than 1 hour)
}
