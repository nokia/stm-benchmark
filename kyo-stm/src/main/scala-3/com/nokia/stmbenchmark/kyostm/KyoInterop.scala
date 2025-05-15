/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import scala.concurrent.Future
import scala.util.Try

import kyo.{ <, Async, Abort, Fiber, IO }

trait KyoInterop {

  final def unsafeForkAndRunSync[A](tsk: A < (Async & Abort[Throwable])): A = {
    import kyo.AllowUnsafe.embrace.danger
    IO.Unsafe.evalOrThrow(
      Async.run(tsk).flatMap(_.block(kyo.Duration.Infinity))
    ).getOrThrow
  }

  final def unsafeRunSyncIO[A](task: A < IO): A = {
    import kyo.AllowUnsafe.embrace.danger
    IO.Unsafe.evalOrThrow(task)
  }

  final def unsafeToFuture[A](tsk: A < (Async & Abort[Throwable])): Future[A] = {
    this.handleAsync(tsk)
  }

  private[this] final def handleAsync[A](tsk: A < (Async & Abort[Throwable])): Future[A] = {
    val futIO: Future[A] < IO = Async.run[Throwable, A, Any](tsk).map(_.toFuture)
    this.handleIO[Future[A]](futIO).flatten
  }

  private[this] final def handleIO[A](tsk: A < (IO & Abort[Throwable])): Future[A] = {
    import kyo.AllowUnsafe.embrace.danger
    Future.fromTry(Try { IO.Unsafe.evalOrThrow(tsk) })
  }
}
