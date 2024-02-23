/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package benchmarks

import java.util.concurrent.TimeUnit.HOURS

import cats.effect.IO

import zio.Task

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import common.{ Solver, Board }
import catsstm.CatsStmSolver
import zstm.ZstmSolver
import choam.RxnSolver
import sequential.SequentialSolver

@Fork(3, jvmArgsAppend = Array("-Dcats.effect.tracing.mode=NONE"))
@Threads(1) // because it runs on a thread-pool
@BenchmarkMode(Array(Mode.AverageTime))
@Timeout(1, HOURS)
class Benchmarks {

  import Benchmarks._

  @Benchmark
  def baseline(st: BaselineState, bh: Blackhole): Unit = {
    bh.consume(
      st.unsafeRunSync(st.solver.solve(st.board)) : Solver.Solution
    )
  }

  @Benchmark
  def catsStm(st: CatsStmState, bh: Blackhole): Unit = {
    bh.consume(
      st.unsafeRunSync(st.solver.solve(st.board)) : Solver.Solution
    )
  }

  @Benchmark
  def rxn(st: RxnState, bh: Blackhole): Unit = {
    bh.consume(
      st.unsafeRunSync(st.solver.solve(st.board)) : Solver.Solution
    )
  }

  @Benchmark
  def zstm(st: ZstmState, bh: Blackhole): Unit = {
    bh.consume(
      st.unsafeRunSync(st.solver.solve(st.board)) : Solver.Solution
    )
  }
}

object Benchmarks {

  @State(Scope.Benchmark)
  abstract class AbstractState {

    @Param(Array("testBoard.txt"))
    var _board: String =
      null

    var board: Board.Normalized =
      null

    @Setup
    def setup(): Unit = {
      // for ZSTM, we want to avoid a CE threadpool
      // existing during the measurement, so we create
      // a separate runtime just for the initialization,
      // and then shut it down:
      val setupRuntime = cats.effect.unsafe.IORuntimeBuilder().build()
      try {
        val b = Board.fromResource[IO](this._board).unsafeRunSync()(setupRuntime)
        this.board = b.normalize
      } finally {
        setupRuntime.shutdown()
      }
    }
  }

  @State(Scope.Benchmark)
  abstract class IOState extends AbstractState {

    protected val runtime =
      cats.effect.unsafe.IORuntime.global

    final def unsafeRunSync[A](tsk: IO[A]): A =
      tsk.unsafeRunSync()(this.runtime)
  }

  @State(Scope.Benchmark)
  class BaselineState extends IOState {
    val solver: Solver[IO] = {
      unsafeRunSync(SequentialSolver[IO](log = false))
    }
  }

  @State(Scope.Benchmark)
  class CatsStmState extends IOState {
    val solver: Solver[IO] = {
      val numCpu = Runtime.getRuntime().availableProcessors()
      unsafeRunSync(CatsStmSolver[IO](txnLimit = 2 * numCpu, parLimit = numCpu, log = false))
    }
  }

  @State(Scope.Benchmark)
  class RxnState extends IOState {
    val solver: Solver[IO] = {
      val numCpu = Runtime.getRuntime().availableProcessors()
      unsafeRunSync(RxnSolver[IO](parLimit = numCpu, log = false))
    }
  }

  @State(Scope.Benchmark)
  class ZstmState extends AbstractState {

    private[this] val runtime = {
      zio.Runtime(
        zio.ZEnvironment.empty,
        zio.FiberRefs.empty,
        zio.RuntimeFlags.disable(zio.RuntimeFlags.default)(zio.RuntimeFlag.FiberRoots),
      )
    }

    val solver: Solver[Task] = {
      val numCpu = Runtime.getRuntime().availableProcessors()
      unsafeRunSync(ZstmSolver(parLimit = numCpu, log = false))
    }

    final def unsafeRunSync[A](tsk: Task[A]): A = {
      zio.Unsafe.unsafe { implicit u =>
        this.runtime.unsafe.run(tsk).getOrThrow()
      }
    }
  }
}
