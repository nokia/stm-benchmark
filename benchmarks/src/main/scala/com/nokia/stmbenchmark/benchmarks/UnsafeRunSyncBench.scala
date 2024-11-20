/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package benchmarks

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

import cats.effect.std.Dispatcher
import cats.effect.IO

import zio.{ Task, ZIO }

import org.openjdk.jmh.annotations._
import java.util.concurrent.CancellationException
import scala.concurrent.duration.FiniteDuration

@Fork(value = 3, jvmArgsAppend = Array(
  "-XX:+UseG1GC",
  "-Dcats.effect.tracing.mode=NONE",
  "-Ddev.tauri.choam.stats=false",
))
@Threads(1) // because it runs on a thread-pool
@BenchmarkMode(Array(Mode.AverageTime))
class UnsafeRunSyncBench {

  import UnsafeRunSyncBench._

  @Benchmark
  def baseline(st: BaseState): Long = {
    st.doBaseline()
  }

  @Benchmark
  def ceUnsafeRunSync(st: CeState): Long = {
    st.doUnsafeRunSync()
  }

  @Benchmark
  def ceUnsafeRunSyncWithoutFatalCb(st: CeState): Long = {
    st.doUnsafeRunSyncWithoutFatalCb()
  }

  @Benchmark
  def ceUnsafeRunSyncWithDispatcher(st: CeDispatcherState): Long = {
    st.doUnsafeRunSyncWithDispatcher()
  }

  @Benchmark
  def zioUnsafeRunSync(st: ZioState): Long = {
    st.doUnsafeRunSync()
  }
}

object UnsafeRunSyncBench {

  @State(Scope.Benchmark)
  class BaseState {

    final val N =
      16

    protected[this] final val refs: Array[AtomicLong] = {
      Array.fill(N) { new AtomicLong }
    }

    final def doBaseline(): Long = {
      val ref = this.refs(ThreadLocalRandom.current().nextInt(N))
      ref.getAndIncrement()
    }
  }

  @State(Scope.Benchmark)
  class CeState extends BaseState {

    protected[this] val tasks: Array[IO[Long]] =
      this.refs.map(ref => IO.cede *> IO { ref.getAndIncrement() })

    protected[this] val runtime: cats.effect.unsafe.IORuntime =
      cats.effect.unsafe.IORuntime.global

    private[this] final def unsafeRunSync[A](tsk: IO[A]): A = {
      tsk.unsafeRunSync()(this.runtime)
    }

    private[this] final def unsafeRunSyncWithoutFatalCb[A](tsk: IO[A]): A = {
      val q = new ArrayBlockingQueue[Either[Throwable, A]](1)
      UnsafeAccess.unsafeRunFiberWithoutFatalCb(
        this.runtime,
        tsk,
        () => {
          q.offer(Left(new CancellationException))
          scala.runtime.BoxedUnit.UNIT
        },
        { ex =>
          q.offer(Left(ex))
          scala.runtime.BoxedUnit.UNIT
        },
        { a =>
          q.offer(Right(a))
          scala.runtime.BoxedUnit.UNIT
        },
      )

      try {
        val res = q.poll(java.lang.Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS)
        res.fold(throw _, a => a)
      } catch {
        case _: InterruptedException =>
          throw new NoSuchElementException
      }
    }

    final def doUnsafeRunSync(): Long = {
      val tsk = this.tasks(ThreadLocalRandom.current().nextInt(N))
      this.unsafeRunSync(tsk)
    }

    final def doUnsafeRunSyncWithoutFatalCb(): Long = {
      val tsk = this.tasks(ThreadLocalRandom.current().nextInt(N))
      this.unsafeRunSyncWithoutFatalCb(tsk)
    }
  }

  @State(Scope.Benchmark)
  class CeDispatcherState extends CeState {

    private[this] val dispatcher: Dispatcher[IO] =
      Dispatcher.sequential[IO].allocated.unsafeRunSync()(this.runtime)._1

    private[this] final def unsafeRunSyncWithDispatcher[A](tsk: IO[A]): A = {
      this.dispatcher.unsafeRunSync(tsk)
    }

    final def doUnsafeRunSyncWithDispatcher(): Long = {
      val tsk = this.tasks(ThreadLocalRandom.current().nextInt(N))
      this.unsafeRunSyncWithDispatcher(tsk)
    }
  }

  @State(Scope.Benchmark)
  class ZioState extends BaseState {

    protected[this] val tasks: Array[Task[Long]] =
      this.refs.map(ref => ZIO.yieldNow *> ZIO.attempt { ref.getAndIncrement() })

    protected[this] val runtime: zio.Runtime[Any] = {
      zio.Runtime(
        zio.ZEnvironment.empty,
        zio.FiberRefs.empty,
        zio.RuntimeFlags.disable(zio.RuntimeFlags.default)(zio.RuntimeFlag.FiberRoots),
      )
    }

    protected[this] final def unsafeRunSync[A](task: Task[A]): A = {
      zio.Unsafe.unsafe { implicit u =>
        this.runtime.unsafe.run(task).getOrThrow()
      }
    }

    final def doUnsafeRunSync(): Long = {
      val tsk = this.tasks(ThreadLocalRandom.current().nextInt(N))
      this.unsafeRunSync(tsk)
    }
  }

  // TODO: @State(Scope.Benchmark)
  class ZioDispatcherState extends ZioState {

    private[this] val ceRuntime = {
      val zioExecutor = this.unsafeRunSync(ZIO.executor)
      val zioBlockingExecutor = this.unsafeRunSync(ZIO.blockingExecutor)
      val zioClock = this.unsafeRunSync(ZIO.clock.map(_.unsafe))
      val zioSched = this.unsafeRunSync(ZIO.clock.flatMap(_.scheduler))
      cats.effect.unsafe.IORuntimeBuilder()
        .setCompute(zioExecutor.asExecutionContext, () => ())
        .setBlocking(zioBlockingExecutor.asExecutionContext, () => ())
        .setScheduler(new cats.effect.unsafe.Scheduler {
          final override def sleep(delay: FiniteDuration, task: Runnable): Runnable = {
            val ct = zioSched.schedule(task, zio.Duration.fromScala(delay))(null)
            () => { ct.apply() }
          }
          final override def nowMillis(): Long =
            zioClock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)(null)
          final override def monotonicNanos(): Long =
            zioClock.nanoTime()(null)
        }, () => ())
        .build()
    }

    private[this] val dispatcher: Dispatcher[Task] = {
      // TODO: this doesn't work due to a missing `Async` instance
      // TODO: Dispatcher.sequential[Task].allocated.unsafeRunSync()(this.ceRuntime)._1
      sys.error("TODO")
    }
  }
}
