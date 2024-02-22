/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.syntax.all._

import munit.Location

abstract class JvmSolverSpec extends AbstractSolverSpec {

  protected def testFromResource(testName: String, resourceName: String)(implicit loc: Location): Unit = {
    test(testName) {
      createSolver.flatMap { solver =>
        Board.fromResource[Tsk](resourceName).flatMap { board =>
          solver.solve(board.normalize).flatMap { solution =>
            printAndCheckSolution(board, solution)
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
