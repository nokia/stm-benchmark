/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam
package rxn

import cats.Show
import cats.syntax.all._

import dev.tauri.choam.core.{ Rxn, Ref, Reactive }
import dev.tauri.choam.unsafe.InRxn

sealed abstract class RefMatrix[A] {

  val height: Int

  val width: Int

  def apply(row: Int, col: Int): Ref[A]

  def debug(debug: Boolean)(implicit s: Show[A]): Rxn[String] = {
    if (debug) {
      val act: Rxn[List[List[A]]] = (0 until height).toList.traverse { row =>
        (0 until width).toList.traverse { col =>
          val ref = apply(row, col)
          ref.get
        }
      }
      act.map(_.map(_.map(s.show).mkString(", ")).mkString("\n"))
    } else {
      Rxn.pure("")
    }
  }

  def debugF[F[_]](debug: Boolean)(implicit s: Show[A], F: Reactive[F]): F[String] = {
    F.run(this.debug(debug))
  }

  final def unsafeDebug(debug: Boolean)(implicit s: Show[A], txn: InRxn): String = {
    import dev.tauri.choam.unsafe.RefSyntax
    if (debug) {
      val llb = List.newBuilder[List[String]]
      for (row <- (0 until height)) {
        val lb = List.newBuilder[String]
        for (col <- (0 until width)) {
          lb += s.show(this(row, col).value)
        }
        llb += lb.result()
      }
      llb.result().map(_.mkString(", ")).mkString("\n")
    } else {
      ""
    }
  }
}

object RefMatrix {

  private[this] val allocStr =
    Ref.Array.AllocationStrategy(sparse = true, flat = true, padded = false)

  def apply[A](h: Int, w: Int, initial: A): Rxn[RefMatrix[A]] = {
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    Ref.array(len, initial, allocStr).map { refArr =>
      new RefMatrixImpl[A](h, w, refArr)
    }
  }

  def unsafeNew[A](h: Int, w: Int, initial: A)(implicit txn: InRxn): RefMatrix[A] = {
    import dev.tauri.choam.unsafe.newRefArray
    require(h >= 0)
    require(w >= 0)
    val len = h * w
    val refArr = newRefArray(len, initial, allocStr)
    new RefMatrixImpl[A](h, w, refArr)
  }

  private[this] final class RefMatrixImpl[A](h: Int, w: Int, refArr: Ref.Array[A]) extends RefMatrix[A] {

    final override val height: Int =
      h

    final override val width: Int =
      w

    final override def apply(row: Int, col: Int): Ref[A] = {
      require((row >= 0) && (row < height))
      require((col >= 0) && (col < width))
      refArr.unsafeApply((row * width) + col)
    }
  }
}
