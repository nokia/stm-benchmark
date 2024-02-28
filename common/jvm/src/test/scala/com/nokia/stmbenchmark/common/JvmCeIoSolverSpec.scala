/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.effect.IO

import munit.{ Location, TestOptions }

abstract class JvmCeIoSolverSpec extends CeIoSolverSpec {

  protected def testFromResource(resourceName: String)(implicit loc: Location): Unit = {
    testFromResource(resourceNameAndOpts = resourceName)(loc)
  }

  protected def testFromResource(resourceNameAndOpts: TestOptions)(implicit loc: Location): Unit = {
    test(resourceNameAndOpts) {
      createSolver.flatMap { solver =>
        Board.fromResource[IO](resourceNameAndOpts.name).flatMap { board =>
          solver.solve(board.normalize).flatMap { solution =>
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

  // Included test resources:

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/testBoard.txt
  // testFromResource("testBoard.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparseshort.txt
  // testFromResource("sparseshort.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparselong.txt
  // testFromResource("sparselong.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/mainboard.txt
  // testFromResource("mainboard.txt")
}