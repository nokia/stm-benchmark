/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import dev.tauri.choam.ChoamRuntime
import dev.tauri.choam.core.RetryStrategy
import dev.tauri.choam.unsafe.{ InRxn, RefSyntax, UnsafeApi, updateRef }

import cats.syntax.traverse._
import cats.effect.kernel.Async
import cats.effect.syntax.concurrent._
import cats.effect.std.Console

import common.{ Solver, Board, Point, Route, BoolMatrix }

object ImpRxnSolver {

  def apply[F[_]](
    rt: ChoamRuntime,
    parLimit: Int,
    log: Boolean,
    strategy: RetryStrategy,
  )(implicit F: Async[F]): F[Solver[F]] = {
    val api: UnsafeApi = UnsafeApi(rt)
    F.pure(
      new Solver[F] {

        import api.{ atomically, atomicallyAsync }

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

          def solveOneRoute(depth: RefMatrix[Int], route: Route): F[List[Point]] = {
            atomicallyAsync(strategy) { implicit txn =>
              if (log) debug(s"Solving $route")
              val cost = expand(depth, route)
              val costStr = cost.unsafeDebug(debug = log)(i => f"$i%2s", txn)
              debug("Cost after `expand`:\n" + costStr)
              val solution = solve(route, cost)
              debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log))
              lay(depth, solution)
              solution
            }
          }

          def expand(depth: RefMatrix[Int], route: Route)(implicit txn: InRxn): RefMatrix[Int] = {
            val startPoint = route.a
            val endPoint = route.b

            val cost = RefMatrix.unsafeNew[Int](h = depth.height, w = depth.width, initial = 0)
            cost(startPoint.y, startPoint.x).value = 1

            var wavefront = List(startPoint)

            var go = true
            while (go) {
              val newWavefront = new scala.collection.mutable.ListBuffer[Point]()
              for (point <- wavefront) {
                val pointCost = cost(point.y, point.x).value
                for (adjacent <- board.adjacentPoints(point)) {
                  if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                    // can't go in that direction
                  } else {
                    val currentCost = cost(adjacent.y, adjacent.x).value
                    val newCost = pointCost + Board.cost(depth(adjacent.y, adjacent.x).value)
                    if ((currentCost == 0) || (newCost < currentCost)) {
                      cost(adjacent.y, adjacent.x).value = newCost
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
                val costAtRouteEnd = cost(endPoint.y, endPoint.x).value
                if (costAtRouteEnd > 0) {
                  val newCosts = newWavefront.map { marked => cost(marked.y, marked.x).value }
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

          def solve(route: Route, cost: RefMatrix[Int])(implicit txn: InRxn): List[Point] = {
            // we're going *back* from the route end:
            val startPoint = route.b
            val endPoint = route.a
            var solution = List(startPoint)
            while (solution.head != endPoint) {
              val adjacent = board.adjacentPoints(solution.head)
              val costs = adjacent.map { a =>
                val aCost = cost(a.y, a.x).value
                (a, aCost)
              }
              val lowestCost = costs.filter(_._2 != 0).minBy(_._2) // TODO: simplify
              solution = lowestCost._1 +: solution
            }

            solution
          }

          def lay(depth: RefMatrix[Int], solution: List[Point])(implicit txn: InRxn): Unit = {
            for (point <- solution) {
              updateRef(depth(point.y, point.x))(_ + 1)
            }
          }

          F.defer {
            val depth = atomically { implicit txn =>
              RefMatrix.unsafeNew[Int](h = board.height, w = board.width, initial = 0)
            }
            val pl = java.lang.Math.max(1, java.lang.Math.min(parLimit, board.numberOrRoutes))
            val solveOne = { (route: Route) =>
              F.map(solveOneRoute(depth, route)) { solution =>
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
