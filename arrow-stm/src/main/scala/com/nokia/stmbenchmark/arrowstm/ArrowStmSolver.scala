/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import cats.effect.Async

import common.{ Solver, Board }

object ArrowStmSolver extends KotlinInterop { interop =>

  def apply[F[_]](parLimit: Int, log: Boolean)(implicit F: Async[F]): F[Solver[F]] = {
    F.pure(
      new Solver[F] {

        private[this] val solverCrt =
          new ArrowStmSolverCrt(parLimit, log)

        final override def solve(board: Board.Normalized): F[Solver.Solution] = {
          interop.faFromCoroutine(solverCrt.solve(board, _))
        }
      }
    )
  }
}
