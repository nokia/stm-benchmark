/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam
package rxn

import cats.data.{ Chain, NonEmptyChain }
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.Async
import cats.effect.std.Console

import dev.tauri.choam.core.{ Rxn, AsyncReactive, RetryStrategy }

import common.{ Solver, Board, Point, Route, BoolMatrix }

/**
 * A solver using `Rxn` and early release ("ER") to
 * optimize (have less conflicts than) `RxnSolver`.
 */
object ErRxnSolver {

  // TODO: A lot of this code is duplicated with
  // TODO: `RxnSolver`; pull out the common methods.

  def apply[F[_]](
    parLimit: Int,
    log: Boolean,
    strategy: RetryStrategy = RxnSolver.spinStrategy,
  )(implicit F: Async[F], ar: AsyncReactive[F]): F[Solver[F]] = {
    val cons = Console.make[F]
    val debugStrategy = if (log) cons.println(strategy) else F.unit
    debugStrategy *> F.pure(new Solver[F] {

      private[this] val _c =
        cons

      private[this] val runConfig: RetryStrategy =
        strategy

      private[this] final def debug(msg: String): Rxn[Unit] = {
        if (log) Rxn.unit.map { _ => println(msg) }
        else Rxn.unit
      }

      private[this] final def debugF(msg: String): F[Unit] = {
        if (log) _c.println(msg)
        else F.unit
      }

      final override def solve(board: Board.Normalized): F[Solver.Solution] = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: RefMatrix[Int], route: Route): F[List[Point]] = {
          val act: Rxn[List[Point]] = for {
            _ <- if (log) debug(s"Solving $route") else Rxn.unit
            cost <- expand(depth, route)
            costStr <- cost.debug(debug = log)(i => f"$i%2s")
            _ <- debug("Cost after `expand`:\n" + costStr)
            solution <- solve(route, cost)
            solutionList = solution.toList
            _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solutionList), debug = log))
            _ <- lay(depth, solution)
          } yield solutionList
          ar.runAsync(act, runConfig)
        }

        def expand(depth: RefMatrix[Int], route: Route): Rxn[RefMatrix[Int]] = {
          val startPoint = route.a
          val endPoint = route.b

          RefMatrix[Int](depth.height, depth.width, 0).flatMap { cost =>
            cost.set(startPoint.y, startPoint.x, 1).flatMap { _ =>

              def go(wavefront: Chain[Point]): Rxn[Chain[Point]] = {
                val mkNewWf = wavefront.traverse { point =>
                  cost.get(point.y, point.x).flatMap { pointCost =>
                    Chain.fromSeq(board.adjacentPoints(point)).traverse { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        Rxn.pure(Chain.empty)
                      } else {
                        cost.get(adjacent.y, adjacent.x).flatMap { currentCost =>
                          val ref = depth.getRef(adjacent.y, adjacent.x) // TODO: this is inefficient
                          Rxn.unsafe.tentativeRead(ref).flatMap { d =>
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost.set(adjacent.y, adjacent.x, newCost).as(Chain(adjacent))
                            } else {
                              Rxn.pure(Chain.empty)
                            }
                          }
                        }
                      }
                    }.map(_.flatten)
                  }
                }.map(_.flatten)

                mkNewWf.flatMap { newWavefront =>
                  if (newWavefront.isEmpty) {
                    Rxn.unsafe.panic(new Solver.Stuck)
                  } else {
                    cost.get(endPoint.y, endPoint.x).flatMap { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        newWavefront.traverse { marked =>
                          cost.get(marked.y, marked.x)
                        }.flatMap { newCosts =>
                          val minimumNewCost = newCosts.minimumOption.get // TODO: partial function
                          if (costAtRouteEnd < minimumNewCost) {
                            // no new location has lower cost than the
                            // cost currently at the route end, so
                            // no reason to continue:
                            Rxn.pure(newWavefront)
                          } else {
                            // continue with the new wavefront:
                            go(newWavefront)
                          }
                        }
                      } else {
                        // continue with the new wavefront:
                        go(newWavefront)
                      }
                    }
                  }
                }
              }

              go(Chain(startPoint)).as(cost)
            }
          }
        }

        def solve(route: Route, cost: RefMatrix[Int]): Rxn[NonEmptyChain[Point]] = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a
          Rxn.monadInstance.iterateWhileM(NonEmptyChain(startPoint)) { solution =>
            val adjacent = board.adjacentPoints(solution.head)
            adjacent.traverse { a =>
              cost.get(a.y, a.x).map(a -> _)
            }.map { costs =>
              val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
              lowestCost._1 +: solution
            }
          } (p = { solution => solution.head != endPoint })
        }

        def lay(depth: RefMatrix[Int], solution: NonEmptyChain[Point]): Rxn[Unit] = {
          solution.traverse_ { point =>
            depth.update(point.y, point.x, _ + 1)
          }
        }

        RefMatrix.apply(
          h = board.height,
          w = board.width,
          initial = 0,
        ).run[F].flatMap { depth =>
          val pl = java.lang.Math.max(1, java.lang.Math.min(parLimit, board.numberOrRoutes))
          val solveOne = { (route: Route) =>
            solveOneRoute(depth, route).map(route -> _)
          }
          val solveAll = if (pl == 1) {
            board.routes.traverse(solveOne)
          } else {
            board.routes.parTraverseN(pl)(solveOne)
          }
          solveAll.flatMap { solutions =>
            val solution = Map(solutions: _*)
            debugF("Full solution:\n" + board.debugSolution(solution, debug = log)).as(Solver.Solution(solution))
          }
        }
      }
    })
  }
}
