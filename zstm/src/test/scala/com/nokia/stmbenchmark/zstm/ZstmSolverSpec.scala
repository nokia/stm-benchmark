/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package zstm

import java.util.concurrent.TimeoutException

import cats.effect.kernel.Async

import zio.{ Task, ZIO }
import zio.interop.catz.implicits.rts

import munit.Location

import common.{ JvmSolverSpec, Solver }

final class ZstmSolverSpec extends JvmSolverSpec {

  final override type Tsk[a] = Task[a]

  protected override def createSolver: Task[Solver[Task]] = {
    ZIO.attempt { Runtime.getRuntime().availableProcessors() }.flatMap { numCpu =>
      ZstmSolver(parLimit = numCpu, log = false)
    }
  }

  protected override def debug(msg: String): Task[Unit] = {
    ZIO.consoleWith { console =>
      console.printLine(msg)
    }
  }

  protected override def assertTsk(cond: Boolean)(implicit loc: Location): Task[Unit] =
    ZIO.attempt { assert(cond) }

  protected override implicit def asyncInstance: Async[Task] =
    zio.interop.catz.asyncInstance

  protected override def munitValueTransform: Option[ValueTransform] = Some(
    new ValueTransform(name = "zio", { case x: Task[_] =>
      zio.Unsafe.unsafe { implicit u =>
        val d = this.munitIOTimeout
        val taskWithTimeout = x.timeoutFailCause(zio.Cause.fail(new TimeoutException(s"zstm test timed out after $d")))(
          zio.Duration.fromScala(d)
        )
        rts.unsafe.runToFuture(taskWithTimeout)
      }
    })
  )
}
