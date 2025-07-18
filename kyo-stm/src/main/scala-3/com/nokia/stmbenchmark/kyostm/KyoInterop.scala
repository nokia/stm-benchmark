/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import scala.concurrent.Future
import scala.util.Try

import kyo.{ <, Sync, Async, Abort, Fiber }

trait KyoInterop {

  final def unsafeForkAndRunSync[A](tsk: A < (Async & Abort[Throwable])): A = {
    import kyo.AllowUnsafe.embrace.danger
    Sync.Unsafe.evalOrThrow(
      Fiber.init(tsk).flatMap(_.block(kyo.Duration.Infinity))
    ).getOrThrow
  }

  final def unsafeRunSyncIO[A](task: A < Sync): A = {
    import kyo.AllowUnsafe.embrace.danger
    Sync.Unsafe.evalOrThrow(task)
  }

  final def unsafeToFuture[A](tsk: A < (Async & Abort[Throwable])): Future[A] = {
    this.handleAsync(tsk)
  }

  private[this] final def handleAsync[A](tsk: A < (Async & Abort[Throwable])): Future[A] = {
    val futIO: Future[A] < Sync = Fiber.init[Throwable, A, Any, Any](tsk).map(_.toFuture)
    this.handleIO[Future[A]](futIO).flatten
  }

  private[this] final def handleIO[A](tsk: A < (Sync & Abort[Throwable])): Future[A] = {
    import kyo.AllowUnsafe.embrace.danger
    Future.fromTry(Try { Sync.Unsafe.evalOrThrow(tsk) })
  }
}
