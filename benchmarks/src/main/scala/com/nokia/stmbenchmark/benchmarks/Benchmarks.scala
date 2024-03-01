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

import common.{ Solver, Board }
import catsstm.CatsStmSolver
import zstm.ZstmSolver
import choam.RxnSolver
import sequential.SequentialSolver

@Fork(value = 3, jvmArgsAppend = Array("-Dcats.effect.tracing.mode=NONE"))
@Threads(1) // because it runs on a thread-pool
@BenchmarkMode(Array(Mode.AverageTime))
@Timeout(time = 1, timeUnit = HOURS)
class Benchmarks {

  import Benchmarks._

  @Benchmark
  def baseline(st: BaselineState): Solver.Solution = {
    st.runSolveTask()
  }

  @Benchmark
  def catsStm(st: CatsStmState): Solver.Solution = {
    st.runSolveTask()
  }

  @Benchmark
  def rxn(st: RxnState): Solver.Solution = {
    st.runSolveTask()
  }

  @Benchmark
  def zstm(st: ZstmState): Solver.Solution = {
    st.runSolveTask()
  }
}

object Benchmarks {

  @State(Scope.Benchmark)
  abstract class AbstractState {

    @Param(Array("testBoard.txt"))
    protected[this] var board: String =
      null

    @Param(Array("42"))
    protected[this] var seed: Long =
      0L

    @Param(Array("0")) // 0 means default to availableProcessors()
    protected[this] var parLimit: Int =
      0

    protected var normalizedBoard: Board.Normalized =
      null

    def runSolveTask(): Solver.Solution

    @Setup
    protected def setup(): Unit = {
      // for ZSTM, we want to avoid a CE threadpool
      // existing during the measurement, so we create
      // a separate runtime just for the initialization,
      // and then shut it down:
      val setupRuntime = cats.effect.unsafe.IORuntimeBuilder().build()
      try {
        val b = Board.fromResource[IO](this.board).unsafeRunSync()(setupRuntime)
        this.normalizedBoard = b.normalize(this.seed)
      } finally {
        setupRuntime.shutdown()
      }
      val pl = this.parLimit match {
        case 0 =>
          Runtime.getRuntime().availableProcessors()
        case pl =>
          pl
      }
      this.parLimit = pl
    }
  }

  @State(Scope.Benchmark)
  abstract class IOState extends AbstractState {

    private[this] var solveTask: IO[Solver.Solution] =
      null.asInstanceOf[IO[Solver.Solution]]

    protected def mkSolver(parLimit: Int): IO[Solver[IO]]

    final override def runSolveTask(): Solver.Solution = {
      this.unsafeRunSync(this.solveTask)
    }

    private[this] val runtime =
      cats.effect.unsafe.IORuntime.global

    protected final def unsafeRunSync[A](tsk: IO[A]): A =
      tsk.unsafeRunSync()(this.runtime)

    @Setup
    protected override def setup(): Unit = {
      super.setup()
      val solver = unsafeRunSync(this.mkSolver(this.parLimit))
      this.solveTask = IO.cede *> solver.solve(this.normalizedBoard)
    }
  }

  @State(Scope.Benchmark)
  class BaselineState extends IOState {
    protected final override def mkSolver(parLimit: Int): IO[Solver[IO]] = {
      SequentialSolver[IO](log = false)
    }
  }

  @State(Scope.Benchmark)
  class CatsStmState extends IOState {
    protected final override def mkSolver(parLimit: Int): IO[Solver[IO]] = {
      // FIXME: txnLimit
      CatsStmSolver[IO](txnLimit = 2L * parLimit, parLimit = parLimit, log = false)
    }
  }

  @State(Scope.Benchmark)
  class RxnState extends IOState {
    protected final override def mkSolver(parLimit: Int): IO[Solver[IO]] = {
      RxnSolver[IO](parLimit = parLimit, log = false)
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

    private[this] var solveTask: Task[Solver.Solution] =
      null.asInstanceOf[Task[Solver.Solution]]

    private[this] final def unsafeRunSync[A](tsk: Task[A]): A = {
      val task = zio.ZIO.yieldNow *> tsk
      zio.Unsafe.unsafe { implicit u =>
        this.runtime.unsafe.run(task).getOrThrow()
      }
    }

    final override def runSolveTask(): Solver.Solution = {
      unsafeRunSync(this.solveTask)
    }

    @Setup
    protected override def setup(): Unit = {
      super.setup()
      val solver = unsafeRunSync(ZstmSolver(parLimit = this.parLimit, log = false))
      this.solveTask = zio.ZIO.yieldNow *> solver.solve(this.normalizedBoard)
    }
  }
}
