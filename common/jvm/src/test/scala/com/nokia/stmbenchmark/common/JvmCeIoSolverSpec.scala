/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.effect.IO

import munit.{ Location, TestOptions }

abstract class JvmCeIoSolverSpec extends CeIoSolverSpec {

  protected[this] def testFromResource(
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
        IO { this.normalizeAndRestrict(board, restrict) }.flatMap { b =>
          // get back on the WSTP before starting the
          // solver (we'll hopefully not block any more):
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
  }
}
