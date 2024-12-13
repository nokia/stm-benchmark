/*
 * © 2023-2024 Nokia
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

  @State(Scope.Benchmark)
  class KyoStmState extends Benchmarks.AbstractState {

    import kyo.{ <, Async, Abort, IO }
    import kyostm.KyoStmSolver

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

    private[this] final def unsafeRunSync[A : kyo.Flat](task: <[A, Async & Abort[Throwable]]): A = {
      import kyo.AllowUnsafe.embrace.danger
      val task2 = Async.runAndBlock(kyo.Duration.Infinity)(task)
      val task3 = Abort.run(task2)
      val result = IO.Unsafe.run(task3).eval
      result.fold(err => throw err.getFailure)(a => a)
    }

    final override def runSolveTask(): Solver.Solution = {
      import kyo.AllowUnsafe.embrace.danger
      IO.Unsafe.run(
        Abort.run(
          Async.run(this.solveTask).flatMap(_.block(kyo.Duration.Infinity))
        ).map(_.getOrThrow)
      ).eval.getOrThrow
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
      val solver = unsafeRunSync(KyoStmSolver(parLimit = n, log = false))
      this.solveTask = repeatKyo(solver.solve(this.normalizedBoard), this.normalizedRepeat)
    }

    private[this] final def repeatKyo[A](tsk: <[A, Async & Abort[Throwable]], n: Int): A < (Async & Abort[Throwable]) = {
      if (n <= 1) tsk
      else tsk.map { _ => repeatKyo(tsk, n - 1) }
    }
  }
}
