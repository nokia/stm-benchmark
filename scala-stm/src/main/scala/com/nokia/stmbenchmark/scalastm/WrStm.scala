/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package scalastm

import scala.concurrent.stm.{ InTxn, atomic }

import cats.{ Show, Id, ~> }
import cats.free.Free
import cats.effect.kernel.Sync

object WrStm {

  sealed abstract class WrStmA[A] extends Product with Serializable
  final case class NewTMatrix[A](h: Int, w: Int, init: A) extends WrStmA[TMatrix[A]]
  final case class GetTm[A](tm: TMatrix[A], row: Int, col: Int) extends WrStmA[A]
  final case class SetTm[A](tm: TMatrix[A], row: Int, col: Int, nv: A) extends WrStmA[Unit]
  final case class ModTm[A](tm: TMatrix[A], row: Int, col: Int, f: A => A) extends WrStmA[Unit]
  final case class DebugTm[A](tm: TMatrix[A], debug: Boolean, show: Show[A]) extends WrStmA[String]
  final case class Raise[A](ex: Throwable) extends WrStmA[A]

  type WrStm[A] = Free[WrStmA, A]

  def unit: WrStm[Unit] =
    Free.pure(())

  def pure[A](a: A): WrStm[A] =
    Free.pure(a)

  def newTMatrix[A](h: Int, w: Int, init: A): WrStm[TMatrix[A]] =
    Free.liftF(NewTMatrix(h = h, w = w, init = init))

  def getTm[A](tm: TMatrix[A], row: Int, col: Int): WrStm[A] =
    Free.liftF(GetTm(tm = tm, row = row, col = col))

  def setTm[A](tm: TMatrix[A], row: Int, col: Int, nv: A): WrStm[Unit] =
    Free.liftF(SetTm(tm = tm, row = row, col = col, nv = nv))

  def modTm[A](tm: TMatrix[A], row: Int, col: Int, f: A => A): WrStm[Unit] =
    Free.liftF(ModTm(tm = tm, row = row, col = col, f = f))

  def debugTm[A](tm: TMatrix[A], debug: Boolean)(implicit show: Show[A]): WrStm[String] =
    Free.liftF(DebugTm(tm = tm, debug = debug, show = show))

  def raiseError[A](ex: Throwable): WrStm[A] =
    Free.liftF(Raise(ex))

  def iterateWhileM[A](init: A)(f: A => WrStm[A])(p: A => Boolean): WrStm[A] =
    Free.catsFreeMonadForFree[WrStmA].iterateWhileM(init)(f)(p)

  /** Side-effecting interpreter */
  private def interpreter[F[_]](txn: InTxn): WrStmA ~> Id = new ~>[WrStmA, Id] {
    final override def apply[A](w: WrStmA[A]): A = w match {
      case NewTMatrix(h, w, init) =>
        TMatrix.apply(h = h, w = w, initial = init)
      case GetTm(tm, row, col) =>
        tm.get(row = row, col = col)(txn)
      case SetTm(tm, row, col, nv) =>
          tm.set(row = row, col = col, a = nv)(txn)
      case ModTm(tm, row, col, f) =>
          val ov = tm.get(row = row, col = col)(txn)
          val nv = f(ov)
          tm.set(row = row, col = col, a = nv)(txn)
      case DebugTm(tm, debug, show) =>
        tm.debug(debug = debug)(show, txn)
      case Raise(ex) =>
        throw ex
    }
  }

  def commit[F[_], A](wrTxn: WrStm[A])(implicit F: Sync[F]): F[A] = {
    F.delay {
      atomic { underlyingTxn =>
        wrTxn.foldMap(interpreter(underlyingTxn))
      }
    }
  }
}
