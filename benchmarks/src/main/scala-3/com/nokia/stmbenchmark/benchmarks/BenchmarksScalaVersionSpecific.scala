/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package benchmarks

import scala.annotation.nowarn

import org.openjdk.jmh.annotations._

import common.Solver

abstract class BenchmarksScalaVersionSpecific {

  import BenchmarksScalaVersionSpecific.KyoStmState

  @Benchmark
  def kyoStm(st: KyoStmState): Solver.Solution = {
    st.runSolveTask()
  }
}

object BenchmarksScalaVersionSpecific {

  import kyo.{ <, Async, Abort }
  import kyostm.{ KyoStmSolver, KyoInterop }

  @State(Scope.Benchmark)
  class KyoStmState extends Benchmarks.AbstractState with KyoInterop {

    @Param(Array("0")) // 0 means availableProcessors()
    @nowarn("msg=unset private variable")
    private[this] var parLimit: Int =
      -1

    @Param(Array("1"))
    @nowarn("msg=unset private variable")
    private[this] var parLimitMultiplier: Int =
      -1

    private[this] var solveTask: Solver.Solution < (Async & Abort[Throwable]) =
      null.asInstanceOf[Solver.Solution]

    final override def runSolveTask(): Solver.Solution = {
      unsafeForkAndRunSync(this.solveTask)
    }

    @Setup
    protected override def setup(): Unit = {
      super.setup()
      val pl = this.parLimit match {
        case 0 =>
          Runtime.getRuntime().availableProcessors()
        case pl =>
          pl
      }
      val plm = this.parLimitMultiplier
      val n = pl * plm
      require(n > 0)
      val solver = unsafeRunSyncIO(KyoStmSolver(parLimit = n, log = false))
      this.solveTask = repeatKyo(solver.solve(this.normalizedBoard), this.normalizedRepeat)
    }

    private[this] final def repeatKyo[A](tsk: <[A, Async & Abort[Throwable]], n: Int): A < (Async & Abort[Throwable]) = {
      if (n <= 1) tsk
      else tsk.andThen(repeatKyo(tsk, n - 1))
    }
  }
}
