/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.effect.IO

import munit.Location

abstract class JvmCeIoSolverSpec extends CeIoSolverSpec {

  protected def testFromResource(resourceName: String, printSolution: Boolean = false)(implicit loc: Location): Unit = {
    test(resourceName) {
      createSolver.flatMap { solver =>
        Board.fromResource[IO](resourceName).flatMap { board =>
          solver.solve(board.normalize).flatMap { solution =>
            if (printSolution) printAndCheckSolution(board, solution)
            else checkSolution(board, solution)
          }
        }
      }
    }
  }

  // Possible test resources:

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/testBoard.txt
  // testFromResource(testName = "testBoard", resourceName = "testBoard.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparseshort.txt
  // testFromResource(testName = "sparseshort", resourceName = "sparseshort.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparselong.txt
  // testFromResource(testName = "sparselong", resourceName = "sparselong.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/mainboard.txt
  // testFromResource(testName = "mainboard", resourceName = "mainboard.txt")
}
