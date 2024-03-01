/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import cats.Show
import cats.syntax.all._

import dev.tauri.choam.{ Ref, Axn, Reactive }

sealed abstract class RefMatrix[A] {

  val height: Int

  val width: Int

  def apply(row: Int, col: Int): Ref[A]

  def debug(debug: Boolean)(implicit s: Show[A]): Axn[String] = {
    if (debug) {
      val act: Axn[List[List[A]]] = (0 until height).toList.traverse { row =>
        (0 until width).toList.traverse { col =>
          val ref = apply(row, col)
          ref.get
        }
      }
      act.map(_.map(_.map(s.show).mkString(", ")).mkString("\n"))
    } else {
      Axn.pure("")
    }
  }

  def debugF[F[_]](debug: Boolean)(implicit s: Show[A], F: Reactive[F]): F[String] = {
    F.apply(this.debug(debug))
  }
}

object RefMatrix {

  private[this] val allocStr =
    Ref.Array.AllocationStrategy(sparse = true, flat = true, padded = false)

  def apply[A](h: Int, w: Int, initial: A): Axn[RefMatrix[A]] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    Ref.array(len, initial, allocStr).map { refArr =>
      new RefMatrix[A] {

        final override val height: Int =
          h

        final override val width: Int =
          w

        final override def apply(row: Int, col: Int): Ref[A] = {
          require((row >= 0) && (row < height))
          require((col >= 0) && (col < width))
          refArr.unsafeGet((row * width) + col)
        }
      }
    }
  }
}
