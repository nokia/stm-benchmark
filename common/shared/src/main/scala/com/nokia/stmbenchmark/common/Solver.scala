/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

abstract class Solver[F[_]] {
  def solve(board: Board.Normalized): F[Solver.Solution]
}

object Solver {

  final case class Solution(value: Map[Route, List[Point]])

  final class Stuck(msg: String) extends Exception(msg) {
    def this() =
      this("solver is stuck")
  }
}
