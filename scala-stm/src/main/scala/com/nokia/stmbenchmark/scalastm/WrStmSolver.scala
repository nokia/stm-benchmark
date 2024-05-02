/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package scalastm

import cats.data.{ Chain, NonEmptyChain }
import cats.syntax.all._
import cats.effect.kernel.Async
import cats.effect.std.Console
import cats.effect.syntax.all._

import common.{ Solver, Board, Point, Route, BoolMatrix }

object WrStmSolver {

  def apply[F[_]](parLimit: Int, log: Boolean)(implicit F: Async[F]): F[Solver[F]] = {

    def debug(msg: String): WrStm[Unit] = {
      if (log) WrStm.unit.map { _ => println(msg) }
      else WrStm.unit
    }

    val _c = Console.make[F]

    def debugF(msg: String): F[Unit] = {
      if (log) _c.println(msg)
      else F.unit
    }

    F.pure(
      new Solver[F] {

        final override def solve(board: Board.Normalized): F[Solver.Solution] = {
          val obstructed = BoolMatrix.obstructedFromBoard(board)

          def solveOneRoute(depth: TMatrix[Int], route: Route): F[List[Point]] = {
            val txn = for {
              _ <- debug(s"Solving $route (thread ${Thread.currentThread()})")
              cost <- expand(depth, route)
              costStr <- WrStm.debugTm(cost, debug = log)(i => f"$i%2s")
              _ <- debug("Cost after `expand`:\n" + costStr)
              solution <- solve(route, cost)
              solutionList = solution.toList
              _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solutionList), debug = log))
              _ <- lay(depth, solution)
            } yield solutionList
            WrStm.commit(txn)
          }

          def expand(depth: TMatrix[Int], route: Route): WrStm[TMatrix[Int]] = {
            val startPoint = route.a
            val endPoint = route.b

            WrStm.newTMatrix(h = depth.height, w = depth.width, 0).flatMap { cost =>
              WrStm.setTm(cost, row = startPoint.y, col = startPoint.x, 1).flatMap { _ =>

                def go(wavefront: Chain[Point]): WrStm[Chain[Point]] = {
                  val mkNewWf = wavefront.foldMapM[WrStm, Chain[Point]] { point =>
                    WrStm.getTm(cost, row = point.y, col = point.x).flatMap { pointCost =>
                      board.adjacentPoints(point).foldMapM[WrStm, Chain[Point]] { adjacent =>
                        if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                          // can't go in that direction
                          WrStm.pure(Chain.empty)
                        } else {
                          WrStm.getTm(cost, row = adjacent.y, col = adjacent.x).flatMap { currentCost =>
                            WrStm.getTm(depth, row = adjacent.y, col = adjacent.x).flatMap { d =>
                              val newCost = pointCost + Board.cost(d)
                              if ((currentCost == 0) || (newCost < currentCost)) {
                                WrStm.setTm(cost, row = adjacent.y, col = adjacent.x, newCost).as(Chain(adjacent))
                              } else {
                                WrStm.pure(Chain.empty)
                              }
                            }
                          }
                        }
                      }
                    }
                  }

                  mkNewWf.flatMap { newWavefront =>
                    if (newWavefront.isEmpty) {
                      WrStm.raiseError(new Solver.Stuck)
                    } else {
                      WrStm.getTm(cost, row = endPoint.y, col = endPoint.x).flatMap { costAtRouteEnd =>
                        if (costAtRouteEnd > 0) {
                          newWavefront.traverse { marked =>
                            WrStm.getTm(cost, row = marked.y, col = marked.x)
                          }.flatMap { newCosts =>
                            val minimumNewCost = newCosts.minimumOption.get // TODO: partial function
                            if (costAtRouteEnd < minimumNewCost) {
                              // no new location has lower cost than the
                              // cost currently at the route end, so
                              // no reason to continue:
                              WrStm.pure(newWavefront)
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

          def solve(route: Route, cost: TMatrix[Int]): WrStm[NonEmptyChain[Point]] = {
            // we're going *back* from the route end:
            val startPoint = route.b
            val endPoint = route.a
            WrStm.iterateWhileM(NonEmptyChain(startPoint)) { solution =>
              val adjacent = board.adjacentPoints(solution.head)
              adjacent.traverse { a =>
                WrStm.getTm(cost, row = a.y, col = a.x).map(a -> _)
              }.map { costs =>
                val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
                lowestCost._1 +: solution
              }
            } (p = { solution => solution.head != endPoint })
          }

          def lay(depth: TMatrix[Int], solution: NonEmptyChain[Point]): WrStm[Unit] = {
            solution.traverse_ { point =>
              WrStm.modTm(depth, row = point.y, col = point.x) { _ + 1 }
            }
          }

          WrStm.commit(WrStm.newTMatrix[Int](
            h = board.height,
            w = board.width,
            init = 0,
          )).flatMap { depth =>
            val solveOne = { (route: Route) =>
              solveOneRoute(depth, route).map(route -> _)
            }
            val solveInParallel = if (parLimit == 1) {
              board.routes.traverse(solveOne)
            } else {
              board.routes.parTraverseN(parLimit)(solveOne)
            }
            solveInParallel.flatMap { solutions =>
              val solution = Map(solutions: _*)
              debugF("Full solution:\n" + board.debugSolution(solution, debug = log)).as(Solver.Solution(solution))
            }
          }
        }
      }
    )
  }
}
