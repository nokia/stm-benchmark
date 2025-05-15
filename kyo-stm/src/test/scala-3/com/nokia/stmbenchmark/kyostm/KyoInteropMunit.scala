/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import scala.concurrent.Future

import kyo.{ <, Abort, Async }

import munit.{ BaseFunSuite, ValueTransforms, Location, TestOptions }

trait KyoInteropMunit extends ValueTransforms with KyoInterop { this: BaseFunSuite =>

  protected final def assertKyo(cond: Boolean, clue: => Any = "assertion failed")(
    implicit loc: Location
  ): Unit < Abort[Throwable] = {
    Abort.catching[Throwable] {
      this.assert(cond = cond, clue = clue)
    }
  }

  protected final def testKyo[A](name: TestOptions)(body: => A < (Async & Abort[Throwable]))(implicit loc: Location): Unit = {
    this.test(name) {
      val tsk: A < (Async & Abort[Throwable]) = body
      val fut: Future[A] = unsafeToFuture(tsk)
      fut
    }
  }
}
