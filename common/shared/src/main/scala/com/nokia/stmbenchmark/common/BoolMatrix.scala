/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

final class BoolMatrix private (
  val height: Int,
  val width: Int,
  arr: Array[Boolean],
) {

  require(height >= 0)
  require(width >= 0)
  require(arr.length == (height * width))

  private def this(b: Board.Normalized) = {
    this(
      height = b.height,
      width = b.width,
      arr = {
        val a = new Array[Boolean](b.height * b.width)
        for (pad <- b.pads) {
          BoolMatrix.unsafeSetTrue(a, b.width)(pad.y, pad.x)
        }
        a
      },
    )
  }

  def apply(row: Int, col: Int): Boolean = {
    require((row >= 0) && (row < height))
    require((col >= 0) && (col < width))
    arr((row * width) + col)
  }
}

object BoolMatrix {

  def obstructedFromBoard(board: Board.Normalized): BoolMatrix = {
    new BoolMatrix(board)
  }

  private def unsafeSetTrue(arr: Array[Boolean], width: Int)(row: Int, col: Int): Unit = {
    arr((row * width) + col) = true
  }
}
