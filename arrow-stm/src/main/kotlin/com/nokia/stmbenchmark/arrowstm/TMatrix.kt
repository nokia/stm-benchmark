/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark.arrowstm

import arrow.fx.stm.STM
import arrow.fx.stm.TArray
import arrow.fx.stm.newTArray

public class TMatrix<A> internal constructor (
  val height: Int,
  val width: Int,
  internal val arr: TArray<A>
) {

  public fun STM.get(row: Int, col: Int): A {
    require((row >= 0) && (row < height))
    require((col >= 0) && (col < width))
    return arr[(row * width) + col]
  }

  public fun STM.set(row: Int, col: Int, a: A): Unit {
    require((row >= 0) && (row < height))
    require((col >= 0) && (col < width))
    arr[(row * width) + col] = a
  }
}

fun <A> STM.newTMatrix(h: Int, w: Int, initial: A): TMatrix<A> {
  require(h >= 0)
  require(w >= 0)
  val len = h * w
  return TMatrix(h, w, newTArray(len, initial))
}
