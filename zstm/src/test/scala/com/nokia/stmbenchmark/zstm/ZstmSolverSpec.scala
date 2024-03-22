/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package zstm

import java.util.concurrent.ThreadLocalRandom

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

  private[this] lazy val solver: Solver[Task] = {
    val mkSolver = ZIO.attempt { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      ZstmSolver(parLimit = numCpu, log = false)
    }
    zio.Unsafe.unsafe { implicit u =>
      this.runtime.unsafe.run(mkSolver).getOrThrow()
    }
  }

  protected def normalize(b: Board): Board.Normalized = {
    val seed = if (b.routes.size > 240) {
      42L
    } else {
      ThreadLocalRandom.current().nextLong()
    }
    b.normalize(seed)
  }

  private def testFromResource(
    resourceNameAndOpts: TestOptions,
    expMaxDepth: Int = -1,
    expTotalCost: Int = -1,
  )(implicit loc: Location): Unit = {
    testZ(resourceNameAndOpts) {
      Board.fromResource[Task](resourceNameAndOpts.name).flatMap { board =>
        solver.solve(this.normalize(board)).flatMap { solution =>
          ZIO.attempt {
            checkSolutionInternal(
              resourceNameAndOpts,
              board,
              solution,
              expMaxDepth = expMaxDepth,
              expTotalCost = expTotalCost,
            )
          }
        }
      }
    }
  }

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/minimal.txt
  testZ("minimal.txt") {
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
      solver.solve(this.normalize(board)).flatMap { solution =>
        ZIO.attempt {
          checkSolutionInternal(
            "minimal.txt".tag(Verbose),
            board,
            solution,
            expMaxDepth = 2,
            expTotalCost = 24,
          )
        }
      }
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_micro.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt".ignore) // very long (approx. 55 mins), but works
  testFromResource("mainboard.txt".ignore) // too long (more than 1 hour)
}
