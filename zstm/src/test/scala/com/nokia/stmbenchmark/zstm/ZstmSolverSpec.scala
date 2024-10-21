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
import munit.FunSuite

import common.{ Board, Solver }
import common.MunitUtils

final class ZstmSolverSpec extends FunSuite with MunitUtils {

  final override def munitTimeout =
    180.minutes

  private[this] val runtime: zio.Runtime[Any] = {
    zio.Runtime(
      zio.ZEnvironment.empty,
      zio.FiberRefs.empty,
      zio.RuntimeFlags.disable(zio.RuntimeFlags.default)(zio.RuntimeFlag.FiberRoots),
    )
  }

  private[this] lazy val solver: Solver[Task] = {
    val mkSolver = ZIO.attempt { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      ZstmSolver(parLimit = numCpu, log = false)
    }
    zio.Unsafe.unsafe { implicit u =>
      this.runtime.unsafe.run(mkSolver).getOrThrow()
    }
  }

  @scala.annotation.nowarn("cat=unchecked")
  override def munitValueTransforms: List[ValueTransform] = {
    new ValueTransform("zio.Task", {
      case tsk: zio.Task[_] =>
        zio.Unsafe.unsafe { implicit u =>
          this.runtime.unsafe.runToFuture(tsk)
        }
    }) :: super.munitValueTransforms
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
    restrict: Int = 0,
    expMaxDepth: Int = -1,
    expTotalCost: Int = -1,
  )(implicit loc: Location): Unit = {
    val nameForMunit = if (restrict != 0) {
      s"${resourceNameAndOpts.name} (restrict = ${restrict})"
    } else {
      resourceNameAndOpts.name
    }
    test(resourceNameAndOpts.withName(nameForMunit)) {
      Board.fromResource[Task](resourceNameAndOpts.name).flatMap { board =>
        val b = this.normalize(board).restrict(restrict)
        solver.solve(b).flatMap { solution =>
          ZIO.attempt {
            checkSolutionInternal(
              resourceNameAndOpts,
              b,
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
  test("minimal.txt") {
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
      val b = this.normalize(board)
      solver.solve(b).flatMap { solution =>
        ZIO.attempt {
          checkSolutionInternal(
            "minimal.txt".tag(Verbose),
            b,
            solution,
            expMaxDepth = 2,
            expTotalCost = 24,
          )
        }
      }
    }
  }

  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt", restrict = 3) // unrestricted takes approx. 50 mins
  testFromResource("mainboard.txt", restrict = 7) // unrestricted takes almost 5 hours
  testFromResource("memboard.txt", restrict = 5) // unrestricted takes almost 2 hours
}
