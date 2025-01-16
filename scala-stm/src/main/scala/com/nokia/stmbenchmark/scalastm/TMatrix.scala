/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package scalastm

import scala.concurrent.stm.{ TArray, InTxn, atomic }

import cats.Show
import cats.effect.kernel.Sync

sealed abstract class TMatrix[A] {

  val height: Int

  val width: Int

  def get(row: Int, col: Int)(implicit txn: InTxn): A

  def set(row: Int, col: Int, a: A)(implicit txn: InTxn): Unit

  def debug(debug: Boolean)(implicit show: Show[A], txn: InTxn): String = {
    if (debug) {
      val llb = List.newBuilder[List[String]]
      for (row <- (0 until height)) {
        val lb = List.newBuilder[String]
        for (col <- (0 until width)) {
          lb += show.show(this.get(row, col))
        }
        llb += lb.result()
      }
      llb.result().map(_.mkString(", ")).mkString("\n")
    } else {
      ""
    }
  }

  def debugF[F[_]](debug: Boolean)(implicit s: Show[A], F: Sync[F]): F[String] = {
    F.delay(atomic { implicit txn =>
      this.debug(debug)
    })
  }
}

object TMatrix {

  def apply[A](h: Int, w: Int, initial: A): TMatrix[A] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    val init = initial.asInstanceOf[AnyRef]
    val tarr = TArray.apply[AnyRef](scala.collection.Iterator.fill(len)(init))

    new TMatrix[A] {

      override val height: Int =
        h

      override val width: Int =
        w

      final override def get(row: Int, col: Int)(implicit txn: InTxn): A = {
        require((row >= 0) && (row < height))
        require((col >= 0) && (col < width))
        tarr.apply((row * width) + col).asInstanceOf[A]
      }

      final override def set(row: Int, col: Int, a: A)(implicit txn: InTxn): Unit = {
        require((row >= 0) && (row < height))
        require((col >= 0) && (col < width))
        tarr.update((row * width) + col, a.asInstanceOf[AnyRef])
      }
    }
  }
}
