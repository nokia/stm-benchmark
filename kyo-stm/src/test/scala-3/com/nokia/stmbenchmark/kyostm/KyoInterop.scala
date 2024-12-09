/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import scala.concurrent.Future

import kyo.{ <, Abort, AllowUnsafe, Async, Fiber, Flat, IO, Result }

import munit.{ BaseFunSuite, ValueTransforms, Location, TestOptions }

trait KyoInterop extends ValueTransforms { this: BaseFunSuite =>

  protected final def assertKyo(cond: Boolean, clue: => Any = "assertion failed")(
    implicit loc: Location
  ): Unit < Abort[Throwable] = {
    Abort.catching[Throwable] {
      this.assert(cond = cond, clue = clue)
    }
  }

  protected final def testKyo[A : Flat](name: TestOptions)(body: => A < (Async & Abort[Throwable]))(implicit loc: Location): Unit = {
    this.test(name) {
      val tsk: A < (Async & Abort[Throwable]) = body
      val fut: Future[A] = handleAsync(tsk)
      fut
    }
  }

  private[this] final def handleAsync[A](tsk: A < (Async & Abort[Throwable]))(implicit f: Flat[A]): Future[A] = {
    val fib: Fiber[Throwable, A] < IO = Async.run[Throwable, A, Any](tsk)
    val futIO: Future[A] < IO = fib.map(_.toFuture)
    handleIO[Future[A]](futIO).flatten
  }

  private[this] final def handleIO[A](tsk: A < (IO & Abort[Throwable]))(implicit f: Flat[A]): Future[A] = {
    import AllowUnsafe.embrace.danger
    val ioResult: Result[Throwable, A] < IO = Abort.run(tsk)
    val result: Result[Throwable, A] = IO.Unsafe.run[Result[Throwable, A], Nothing](ioResult).eval
    result.fold (err => Future.failed(err.getFailure))(Future.successful)
  }
}
