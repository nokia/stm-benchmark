/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package catsstm

import cats.Show
import cats.syntax.all._

import io.github.timwspence.cats.stm.STM

sealed abstract class TMatrix[F[_], S <: STM[F] with Singleton, A] {

  val stm: S

  val height: Int

  val width: Int

  def apply(row: Int, col: Int): stm.TVar[A]

  def debug(debug: Boolean)(implicit s: Show[A]): stm.Txn[String] = {
    if (debug) {
      val t: stm.Txn[List[List[A]]] = (0 until height).toList.traverse { row =>
        (0 until width).toList.traverse { col =>
          val tv = apply(row, col)
          tv.get
        }
      }
      t.map(_.map(_.map(s.show).mkString(", ")).mkString("\n"))
    } else {
      stm.pure("")
    }
  }

  def debugF(debug: Boolean)(implicit s: Show[A]): F[String] = {
    stm.commit(this.debug(debug))
  }
}

object TMatrix {

  def apply[F[_], S <: STM[F] with Singleton, A](s: S)(h: Int, w: Int, initial: A): s.Txn[TMatrix[F, S, A]] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    s.Txn.monadForTxn.replicateA(len, s.TVar.of(initial)).map { list =>

      val newMat = new TMatrix[F, S, A] {

        final val stm: s.type =
          s

        final val height = h

        final val width = w

        private[this] val arr =
          list.toArray

        final override def apply(row: Int, col: Int): stm.TVar[A] = {
          require((row >= 0) && (row < height))
          require((col >= 0) && (col < width))
          arr((row * width) + col)
        }
      }

      newMat : TMatrix[F, S, A]
    }
  }
}
