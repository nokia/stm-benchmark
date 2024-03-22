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

  private[this] lazy val solver: Solver[IO] =
    this.createSolver.unsafeRunSync()

  protected def testFromResource(
    resourceNameAndOpts: TestOptions,
    restrict: Int = 0,
  )(implicit loc: Location): Unit = {
    val nameForMunit = if (restrict != 0) {
      s"${resourceNameAndOpts.name} (restrict = ${restrict})"
    } else {
      resourceNameAndOpts.name
    }
    test(resourceNameAndOpts.withName(nameForMunit)) {
      val resourceName = resourceNameAndOpts.name
      Board.fromResource[IO](resourceName).flatMap { board =>
        // get back on the WSTP before starting the
        // solver (we'll hopefully not block any more):
        val b = this.normalize(board).restrict(restrict)
        IO.cede *> solver.solve(b).flatMap { solution =>
          IO {
            checkSolutionInternal(
              resourceNameAndOpts,
              b,
              solution,
            )
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

  // A very small version of sparselong.txt (see below):
  // testFromResource("sparselong_micro.txt")

  // A somewhat small version of sparselong.txt (see below):
  // testFromResource("sparselong_mini.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/sparselong.txt
  // testFromResource("sparselong.txt")

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/mainboard.txt
  // testFromResource("mainboard.txt")
}
