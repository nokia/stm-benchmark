/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import java.util.concurrent.atomic.AtomicInteger

import kyo.{ <, Async, Abort, IO, STM, TRef, Fiber }

import munit.FunSuite

import common.MunitUtils

final class KyoInteropSpec extends FunSuite with KyoInteropMunit with MunitUtils {

  final class MyException(msg: String) extends Exception(msg)

  test("unsafeRunSyncIO successful") {
    val tsk = IO { 42 }
    assertEquals(unsafeRunSyncIO(tsk), 42)
  }

  test("unsafeRunSyncIO exception".fail) {
    val tsk: Int < IO = IO { throw new Exception("x"); 42 }
    assertEquals(unsafeRunSyncIO(tsk), 42)
  }

  test("unsafeRunSyncIO panic".fail) {
    val tsk: Int < IO = Abort.panic(new MyException("y"))
    assertEquals(unsafeRunSyncIO(tsk), 42)
  }

  test("unsafeForkAndRunSync successful") {
    val tsk: Int < (Async & Abort[Throwable]) = IO { 42 }
    assertEquals(unsafeForkAndRunSync(tsk), 42)
  }

  test("unsafeForkAndRunSync panic in IO".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = Abort.panic(new MyException("p"))
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync exception in IO".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = IO { throw new Exception("q"); 42 }
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync exception in IO map".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = IO { 42 }.map { i =>
      throw new Exception("r")
      i + 1
    }
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync panic in Async map".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = Async.run(IO { 42 }).map { fiber =>
      fiber.get.map { _ =>
        Abort.panic(new MyException("s"))
      }
    }
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync exception in Async map".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = Async.run(IO { 42 }).map { fiber =>
      fiber.get.map { i =>
        throw new Exception("t")
        i + 1
      }
    }
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync exception in STM map".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = TRef.init(42).map { ref =>
      STM.run(ref.get.map { i =>
        throw new Exception("u")
        i + 1
      })
    }
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync panic in STM map".fail) {
    val tsk: Int < (Async & Abort[Throwable]) = TRef.init(42).map { ref =>
      STM.run(ref.get.map[Int, STM] { _ =>
        Abort.panic(new MyException("v"))
      })
    }
    unsafeForkAndRunSync(tsk)
  }

  test("unsafeForkAndRunSync successful STM") {
    val tsk: Int < (Async & Abort[Throwable]) = TRef.init(42).map { ref =>
      STM.run(ref.get.map { i =>
        i + 1
      })
    }
    assertEquals(unsafeForkAndRunSync(tsk), 43)
  }

  test("unsafeToFuture successful") {
    val i = new AtomicInteger
    val tsk: Unit < (Async & Abort[Throwable]) = IO { i.set(42) }
    this.unsafeToFuture(tsk).map { _ =>
      assertEquals(i.get(), 42)
    } (this.munitExecutionContext)
  }

  testKyo("unsafeToFuture exception in IO".fail) {
    val tsk: Unit < (Async & Abort[Throwable]) = IO {
      throw new Exception("a")
    }
    tsk
  }

  testKyo("unsafeToFuture exception outside".fail) {
    (throw new Exception("b")) : Int
  }

  testKyo("unsafeToFuture exception in STM map".fail) {
    val txn: Int < STM = TRef.init(42).map { ref =>
      throw new Exception("c")
      ref.get
    }
    STM.run(txn).map { i =>
      assertKyo(i == 42)
    }
  }

  testKyo("unsafeToFuture exception in Async map".fail) {
    val fork: Fiber[Throwable, Int] < Async = Async.run(42)
    fork.map { fib =>
      fib.get.map { i =>
        throw new Exception("d")
        i + 1
      }
    }
  }
}
