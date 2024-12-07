/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import kyo.{ <, Abort, Async, IO, STM, Kyo }

import common.{ Board, BoolMatrix, Point, Route, Solver }

object KyoStmSolver {

  def apply(log: Boolean): Solver[<[*, Async & Abort[Throwable]]] < Async = {
    new Solver[<[*, Async & Abort[Throwable]]] {

      private[this] final def debug(msg: String): Unit < IO = {
        if (log) IO { println(msg) }
        else ()
      }

      final override def solve(board: Board.Normalized): Solver.Solution < (Async & Abort[Throwable]) = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: TMatrix[Int], route: Route): List[Point] < (Async & Abort[Throwable]) = {
          val txn: List[Point] < STM = (if (log) debug(s"Solving $route") else () : Unit < IO).map { _ =>
            expand(depth, route).map { cost =>
              val costStr = cost.debug(debug = log)(i => f"$i%2s")
              debug("Cost after `expand`:\n" + costStr).map { _ =>
                solve(route, cost).map { solution =>
                  debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log)).map { _ =>
                    lay(depth, solution).map(_ => solution)
                  }
                }
              }
            }
          }

          STM.run(txn)
        }

        def expand(depth: TMatrix[Int], route: Route): TMatrix[Int] < STM = {
          ???
        }

        def solve(route: Route, cost: TMatrix[Int]): List[Point] < STM = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a

          def go(solution: List[Point]): List[Point] < STM = {
            if (solution.head == endPoint) {
              // we're done
              solution
            } else {
              val adjacent = board.adjacentPoints(solution.head)
              Kyo.foreach(adjacent) { a =>
                cost(a.y, a.x).map(a -> _)
              }.map { costs =>
                val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
                go(lowestCost._1 :: solution)
              }
            }
          }

          go(List(startPoint))
        }

        def lay(depth: TMatrix[Int], solution: List[Point]): Unit < STM = {
          ???
        }

        STM.run(TMatrix.apply[Int](h = board.height, w = board.width, initial = 0)).map { depth =>
          ??? : Solver.Solution
        }
      }
    }
  }
}
