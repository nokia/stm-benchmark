/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package zstm

import zio.{ Task, ZIO }
import zio.interop.catz.asyncInstance

import fs2.Stream

import munit.Location
import munit.ZSuite

import common.{ Board, Solver }

final class ZstmSolverSpec extends ZSuite {

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

  private def printAndCheckSolution(board: Board, solution: Solver.Solution)(implicit loc: Location): Task[Unit] =
    debug(board.debugSolution(solution.value)) *> ZIO.attempt { assert(board.isSolutionValid(solution.value)) }

  private def testFromResource(testName: String, resourceName: String)(implicit loc: Location): Unit = {
    testZ(testName) {
      createSolver.flatMap { solver =>
        Board.fromResource[Task](resourceName).flatMap { board =>
          solver.solve(board.normalize).flatMap { solution =>
            printAndCheckSolution(board, solution)
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
        solver.solve(board.normalize).flatMap { solution =>
          printAndCheckSolution(board, solution)
        }
      }
    }
  }

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/testBoard.txt
  testFromResource(testName = "testBoard", resourceName = "testBoard.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparseshort.txt
  // testFromResource(testName = "sparseshort", resourceName = "sparseshort.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparselong.txt
  // testFromResource(testName = "sparselong", resourceName = "sparselong.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/mainboard.txt
  // testFromResource(testName = "mainboard", resourceName = "mainboard.txt")
  // TODO: this times out
}
