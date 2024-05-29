/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark.arrowstm

import kotlinx.coroutines.delay

import arrow.fx.stm.TVar
import arrow.fx.stm.STM
import arrow.fx.stm.atomically

class TMatrixTest {

  suspend fun test1(): Unit {
    val m = atomically {
      val m = newTMatrix(4, 4, "foo")
      m.run { set(1, 1, "bar") }
      m
    }
    val v01 = atomically {
      m.run { get(0, 1) }
    }
    check(v01 == "foo")
    val v11 = atomically {
      m.run { get(1, 1) }
    }
    check(v11 == "bar")
  }
}
