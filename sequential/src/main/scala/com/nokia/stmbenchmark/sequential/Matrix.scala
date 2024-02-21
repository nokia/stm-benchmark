/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package sequential

import cats.{ Show, Monad }
import cats.effect.kernel.{ Ref, Async }
import cats.syntax.all._

sealed abstract class Matrix[F[_], A]()(implicit F: Monad[F]) {

  val height: Int

  val width: Int

  def apply(row: Int, col: Int): Ref[F, A]

  def debug(debug: Boolean)(implicit s: Show[A]): F[String] = {
    if (debug) {
      val t: F[List[List[A]]] = (0 until height).toList.traverse { row =>
        (0 until width).toList.traverse { col =>
          val ref = apply(row, col)
          ref.get
        }
      }
      t.map(_.map(_.map(s.show).mkString(", ")).mkString("\n"))
    } else {
      F.pure("")
    }
  }
}

object Matrix {

  def apply[F[_], A](h: Int, w: Int, initial: A)(implicit F: Async[F]): F[Matrix[F, A]] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    F.replicateA(len, F.ref(initial)).map { list =>
      new Matrix[F, A] {

        final val height = h

        final val width = w

        private[this] val arr =
          list.toArray

        final override def apply(row: Int, col: Int): Ref[F, A] = {
          require((row >= 0) && (row < height))
          require((col >= 0) && (col < width))
          arr((row * width) + col)
        }
      }
    }
  }
}
