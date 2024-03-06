/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package zstm

import cats.Show

import zio.stm.{ USTM, ZSTM, TArray }

final class TMatrix[A] private (val height: Int, val width: Int, arr: TArray[A]) {

  def apply(row: Int, col: Int): USTM[A] = {
    arr((row * width) + col)
  }

  def set(row: Int, col: Int, a: A): USTM[Unit] = {
    arr.update((row * width) + col, { _ => a })
  }

  def modify(row: Int, col: Int, f: A => A): USTM[Unit] = {
    arr.update((row * width) + col, f)
  }

  def debug(debug: Boolean)(implicit A: Show[A]): USTM[String] = {
    if (debug) {
      arr.fold((List.empty[List[String]], 0)) { (state, a) =>
        val (lst, idx) = state
        if ((idx % width) == 0) {
          val newRow = List(A.show(a))
          (newRow :: lst, idx + 1)
        } else {
          val newRow = A.show(a) :: lst.head
          (newRow :: lst.tail, idx + 1)
        }
      }.map { case (lst: List[List[String]], _) =>
        lst.map(_.mkString(", ")).mkString("\n")
      }
    } else {
      ZSTM.succeed("")
    }
  }
}

object TMatrix {

  def apply[A](h: Int, w: Int, initial: A): USTM[TMatrix[A]] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    TArray.make(List.fill(len)(initial): _*).map { tarr =>
      new TMatrix[A](h, w, tarr)
    }
  }
}
