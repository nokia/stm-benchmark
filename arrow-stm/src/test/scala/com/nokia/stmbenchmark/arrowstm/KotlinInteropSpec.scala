/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import scala.concurrent.duration._

import cats.effect.IO

import munit.FunSuite

final class KotlinInteropSpec extends FunSuite with KotlinInterop { interop =>

  test("ioRuntimeFromCoroutineDispatchers") {
    val rt = interop.ioRuntimeFromCoroutineDispatchers()
    try {
      val tsk = testIoRt
      tsk.unsafeRunSync()(rt)
    } finally {
      rt.shutdown()
    }
  }

  private def testIoRt: IO[Unit] = {
    for {
      _ <- IO.cede // go to compute pool
      v <- IO.blocking { 42 } // go to blocking pool
      _ <- IO(assertEquals(v, 42))
      _ <- IO.cede // go back to compute pool
      _ <- IO.sleep(0.1.second) // test scheduler
      fib <- IO.pure(42).delayBy(0.1.second).start
      v <- fib.joinWithNever
      _ <- IO(assertEquals(v, 42))
    } yield ()
  }
}
