/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import cats.Show

import kyo.{ <, Kyo, STM, TRef }

final class TMatrix[A] private (val height: Int, val width: Int, arr: Array[TRef[A]]) {

  def apply(row: Int, col: Int): A < STM = {
    arr((row * width) + col).get
  }

  def set(row: Int, col: Int, a: A): Unit < STM = {
    arr((row * width) + col).set(a)
  }

  def modify(row: Int, col: Int, f: A => A): Unit < STM = {
    arr((row * width) + col).update(f)
  }

  def debug(debug: Boolean)(implicit A: Show[A]): String < STM = {
    if (debug) {
      def go(idx: Int, acc: List[List[String]]): List[List[String]] < STM = {
        if (idx >= arr.length) {
          acc
        } else {
          arr(idx).get.map { a =>
            if ((idx % width) == 0) {
              val newRow = List(A.show(a))
              go(idx + 1, newRow :: acc)
            } else {
              val newRow = A.show(a) :: acc.head
              go(idx + 1, newRow :: acc.tail)
            }
          }
        }
      }

      go(0, Nil).map { lst =>
        lst.map(_.mkString(", ")).mkString("\n")
      }
    } else {
      ""
    }
  }
}

object TMatrix {

  def apply[A](h: Int, w: Int, initial: A): TMatrix[A] < STM = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    Kyo.fill(len) { TRef.init(initial) }.map { ch =>
      val arr = ch.toArray
      new TMatrix(h, w, arr)
    }
  }
}
