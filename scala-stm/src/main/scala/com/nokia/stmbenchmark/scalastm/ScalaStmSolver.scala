/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package scalastm

import scala.concurrent.stm.{ atomic, InTxn }

import cats.syntax.traverse._
import cats.effect.kernel.Async
import cats.effect.syntax.concurrent._
import cats.effect.std.Console

import common.{ Solver, Board, Point, Route, BoolMatrix }

object ScalaStmSolver {

  def apply[F[_]](parLimit: Int, log: Boolean)(implicit F: Async[F]): F[Solver[F]] = {
    F.pure(
      new Solver[F] {

        private[this] final def debug(msg: String): Unit = {
          if (log) println(msg)
          else ()
        }

        private[this] val _c =
          Console.make[F]

        private[this] final def debugF(msg: String): F[Unit] = {
          if (log) _c.println(msg)
          else F.unit
        }

        final override def solve(board: Board.Normalized): F[Solver.Solution] = {
          val obstructed = BoolMatrix.obstructedFromBoard(board)

          def solveOneRoute(depth: TMatrix[Int], route: Route): List[Point] = {
            atomic { implicit txn =>
              if (log) debug(s"Solving $route")
              val cost = expand(depth, route)
              val costStr = cost.debug(debug = log)(i => f"$i%2s", txn)
              debug("Cost after `expand`:\n" + costStr)
              val solution = solve(route, cost)
              debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log))
              lay(depth, solution)
              solution
            }
          }

          def expand(depth: TMatrix[Int], route: Route)(implicit txn: InTxn): TMatrix[Int] = {
            val startPoint = route.a
            val endPoint = route.b

            val cost = TMatrix[Int](h = depth.height, w = depth.width, initial = 0)
            cost.set(startPoint.y, startPoint.x, 1)

            var wavefront = List(startPoint)

            var go = true
            while (go) {
              val newWavefront = new scala.collection.mutable.ListBuffer[Point]()
              for (point <- wavefront) {
                val pointCost = cost.get(point.y, point.x)
                for (adjacent <- board.adjacentPoints(point)) {
                  if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                    // can't go in that direction
                  } else {
                    val currentCost = cost.get(adjacent.y, adjacent.x)
                    val newCost = pointCost + Board.cost(depth.get(adjacent.y, adjacent.x))
                    if ((currentCost == 0) || (newCost < currentCost)) {
                      cost.set(adjacent.y, adjacent.x, newCost)
                      newWavefront += adjacent
                    } else {
                      // not better
                    }
                  }
                }
              }

              if (newWavefront.isEmpty) {
                throw new Solver.Stuck
              } else {
                val costAtRouteEnd = cost.get(endPoint.y, endPoint.x)
                if (costAtRouteEnd > 0) {
                  val newCosts = newWavefront.map { marked => cost.get(marked.y, marked.x) }
                  val minimumNewCost = newCosts.min
                  if (costAtRouteEnd < minimumNewCost) {
                    // no new location has lower cost than the
                    // cost currently at the route end, so
                    // no reason to continue:
                    go = false
                  } else {
                    // continue with the new wavefront:
                    wavefront = newWavefront.result()
                  }
                } else {
                  // continue with the new wavefront:
                  wavefront = newWavefront.result()
                }
              }
            }

            cost
          }

          def solve(route: Route, cost: TMatrix[Int])(implicit txn: InTxn): List[Point] = {
            // we're going *back* from the route end:
            val startPoint = route.b
            val endPoint = route.a
            var solution = List(startPoint)
            while (solution.head != endPoint) {
              val adjacent = board.adjacentPoints(solution.head)
              val costs = adjacent.map { a =>
                val aCost = cost.get(a.y, a.x)
                (a, aCost)
              }
              val lowestCost = costs.filter(_._2 != 0).minBy(_._2) // TODO: simplify
              solution = lowestCost._1 +: solution
            }

            solution
          }

          def lay(depth: TMatrix[Int], solution: List[Point])(implicit txn: InTxn): Unit = {
            for (point <- solution) {
              val ov: Int = depth.get(point.y, point.x)
              depth.set(point.y, point.x, ov + 1)
            }
          }

          F.defer {
            val depth = TMatrix[Int](h = board.height, w = board.width, initial = 0)
            val pl = java.lang.Math.max(1, java.lang.Math.min(parLimit, board.numberOrRoutes))
            val solveOne = { (route: Route) =>
              F.delay {
                val solution = solveOneRoute(depth, route)
                (route, solution)
              }
            }
            val solveAll = if (pl == 1) {
              board.routes.traverse(solveOne)
            } else {
              board.routes.parTraverseN(pl)(solveOne)
            }
            F.flatMap(solveAll) { solutions =>
              val solution = Map(solutions: _*)
              F.as(debugF("Full solution:\n" + board.debugSolution(solution, debug = log)), Solver.Solution(solution))
            }
          }
        }
      }
    )
  }
}
