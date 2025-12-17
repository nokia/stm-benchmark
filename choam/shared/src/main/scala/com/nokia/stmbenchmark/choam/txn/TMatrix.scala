/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam
package txn

import cats.Show
import cats.syntax.all._

import dev.tauri.choam.stm.{ Txn, TArray, Transactive }

sealed abstract class TMatrix[A] {

  val height: Int

  val width: Int

  def get(row: Int, col: Int): Txn[A]

  def set(row: Int, col: Int, a: A): Txn[Unit]

  def update(row: Int, col: Int, f: A => A): Txn[Unit]

  final def debug(debug: Boolean)(implicit s: Show[A]): Txn[String] = {
    if (debug) {
      val act: Txn[List[List[A]]] = (0 until height).toList.traverse { row =>
        (0 until width).toList.traverse { col =>
          get(row, col)
        }
      }
      act.map(_.map(_.map(s.show).mkString(", ")).mkString("\n"))
    } else {
      Txn.pure("")
    }
  }

  final def debugF[F[_]](debug: Boolean)(implicit s: Show[A], F: Transactive[F]): F[String] = {
    F.commit(this.debug(debug))
  }
}

object TMatrix {

  private[this] val allocStr =
    TArray.DefaultAllocationStrategy

  def apply[A](h: Int, w: Int, initial: A): Txn[TMatrix[A]] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    TArray(len, initial, allocStr).map { tArr =>
      new TMatrixImpl[A](h, w, tArr)
    }
  }

  private[this] final class TMatrixImpl[A](h: Int, w: Int, tArr: TArray[A]) extends TMatrix[A] {

    final override val height: Int =
      h

    final override val width: Int =
      w

    final override def get(row: Int, col: Int): Txn[A] =
      tArr.unsafeGet((row * width) + col)

    final override def set(row: Int, col: Int, a: A): Txn[Unit] =
      tArr.unsafeSet((row * width) + col, a)

    final override def update(row: Int, col: Int, f: A => A): Txn[Unit] =
      tArr.unsafeUpdate((row * width) + col, f)
  }
}
